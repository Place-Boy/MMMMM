package com.mmmmm.server;

import com.mmmmm.Config;
import com.mmmmm.MMMMM;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

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
            try {
                String requestPath = exchange.getRequestURI().getPath();
                MMMMM.LOGGER.info("Received request: " + requestPath);

                // Resolve the requested file path
                Path filePath = FILE_DIRECTORY.resolve(requestPath.substring(1)).normalize();

                // Security: Ensure the resolved file path is within the allowed directory
                if (!filePath.startsWith(FILE_DIRECTORY)) {
                    MMMMM.LOGGER.warn("Unauthorized access attempt: " + filePath);
                    exchange.sendResponseHeaders(403, -1); // Forbidden
                    return;
                }

                // Check if the requested file exists
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    MMMMM.LOGGER.warn("File not found: " + filePath);
                    exchange.sendResponseHeaders(404, -1); // Not Found
                    return;
                }

                // Set Content-Type based on file extension
                String contentType = requestPath.endsWith(".zip") ? "application/zip" : "application/octet-stream";
                exchange.getResponseHeaders().add("Content-Type", contentType);

                // Read the file and write it to the HTTP response
                byte[] fileBytes = Files.readAllBytes(filePath);
                exchange.sendResponseHeaders(200, fileBytes.length);
                exchange.getResponseBody().write(fileBytes);

                MMMMM.LOGGER.info("Successfully served file: " + filePath);

            } catch (Exception e) {
                MMMMM.LOGGER.error("Error processing request", e);
                try {
                    exchange.sendResponseHeaders(500, -1); // Internal Server Error
                } catch (IOException ioException) {
                    MMMMM.LOGGER.error("Failed to send error response", ioException);
                }
            } finally {
                exchange.close();
            }
        });

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