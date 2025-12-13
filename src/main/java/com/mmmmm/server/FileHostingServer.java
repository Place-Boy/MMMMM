package com.mmmmm.server;

import com.mmmmm.Config;
import com.mmmmm.MMMMM;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.concurrent.Executors;

/**
 * Manages the file hosting server for ExampleMod.
 */
public class FileHostingServer {

    private static HttpServer fileHostingServer;
    public static final int FILE_SERVER_PORT = Config.fileServerPort;
    public static final Path FILE_DIRECTORY = Path.of("MMMMM/shared-files");

    /**
     * Starts the file hosting server on a separate thread.
     */
    public static void start() throws IOException {
        // Create the shared-files directory if it does not exist
        if (!Files.exists(FILE_DIRECTORY)) {
            Files.createDirectories(FILE_DIRECTORY);
        }

        // Create and configure the HTTP server
        fileHostingServer = HttpServer.create(new InetSocketAddress(FILE_SERVER_PORT), 0);

        fileHostingServer.createContext("/", exchange -> {
            boolean responseStarted = false;
            boolean clientAborted = false;
            long bytesSent = 0L;
            try {
                String requestPath = exchange.getRequestURI().getPath();
                var remote = exchange.getRemoteAddress();
                MMMMM.LOGGER.info("Received request: {} from {}", requestPath, remote);

                // Resolve the requested file path
                Path filePath = FILE_DIRECTORY.resolve(requestPath.substring(1)).normalize();

                // Security: Ensure the resolved file path is within the allowed directory
                if (!filePath.startsWith(FILE_DIRECTORY)) {
                    MMMMM.LOGGER.warn("Unauthorized access attempt: {} from {}", filePath, remote);
                    exchange.sendResponseHeaders(403, -1); // Forbidden
                    return;
                }

                // Check if the requested file exists
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    MMMMM.LOGGER.warn("File not found: {} (from {})", filePath, remote);
                    exchange.sendResponseHeaders(404, -1); // Not Found
                    return;
                }

                long fileSize = Files.size(filePath);
                if (fileSize == 0) {
                    MMMMM.LOGGER.warn("Requested file exists but is empty: {} (from {})", filePath, remote);
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                // Set Content-Type based on file extension
                String contentType = requestPath.endsWith(".zip") ? "application/zip" : "application/octet-stream";
                exchange.getResponseHeaders().add("Content-Type", contentType);

                // Stream the file to the HTTP response while computing SHA-256
                MessageDigest md = MessageDigest.getInstance("SHA-256");

                // Prefer fixed-length response; HttpServer will use FixedLengthOutputStream
                exchange.sendResponseHeaders(200, fileSize);
                responseStarted = true;

                try (InputStream in = Files.newInputStream(filePath);
                     DigestInputStream dis = new DigestInputStream(in, md);
                     OutputStream out = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = dis.read(buffer)) != -1) {
                        try {
                            out.write(buffer, 0, read);
                            bytesSent += read;
                        } catch (IOException writeEx) {
                            // Most likely the client disconnected ("stream closed" / broken pipe). Treat as benign.
                            clientAborted = true;
                            String msg = writeEx.getMessage();
                            if (msg != null && msg.toLowerCase().contains("stream closed")) {
                                MMMMM.LOGGER.info("Client aborted download of {} from {} after {} bytes (stream closed)", filePath, remote, bytesSent);
                            } else {
                                MMMMM.LOGGER.info("Client aborted download of {} from {} after {} bytes ({}).", filePath, remote, bytesSent, writeEx.toString());
                            }
                            break;
                        }
                    }
                    // No explicit flush; closing the stream is sufficient.
                }

                String sha256 = HexFormat.of().formatHex(md.digest());
                if (!clientAborted) {
                    MMMMM.LOGGER.info("Successfully served file: {} (fileSize={} bytes, bytesSent={}, sha256={})", filePath, fileSize, bytesSent, sha256);
                    if (bytesSent < fileSize) {
                        MMMMM.LOGGER.warn("Sent fewer bytes ({}) than the file size ({}) for {} — file may have changed during read.", bytesSent, fileSize, filePath);
                    }
                }

            } catch (IOException e) {
                if (!clientAborted) {
                    MMMMM.LOGGER.error("I/O error while processing request", e);
                    try {
                        if (!responseStarted) {
                            exchange.sendResponseHeaders(500, -1); // Internal Server Error
                        }
                    } catch (IOException ioException) {
                        MMMMM.LOGGER.error("Failed to send error response", ioException);
                    }
                }
            } catch (Exception e) {
                MMMMM.LOGGER.error("Unexpected error processing request", e);
                try {
                    if (!responseStarted) {
                        exchange.sendResponseHeaders(500, -1); // Internal Server Error
                    }
                } catch (IOException ioException) {
                    MMMMM.LOGGER.error("Failed to send error response", ioException);
                }
            } finally {
                exchange.close();
            }
        });
        fileHostingServer.setExecutor(Executors.newCachedThreadPool());
        // Start the server on a separate thread
        new Thread(() -> {
            fileHostingServer.start();
            MMMMM.LOGGER.info("File hosting server started on port " + FILE_SERVER_PORT);
        }).start();
    }

    /**
     * Stops the file hosting server.
     */
    public static void stop() {
        if (fileHostingServer != null) {
            fileHostingServer.stop(0);
            MMMMM.LOGGER.info("File hosting server stopped.");
        }
    }
}