package com.mmmmm.server;

import com.mmmmm.MMMMM;
import com.sun.net.httpserver.HttpServer;
import com.mmmmm.Config;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHostingServer {

    private static HttpServer fileHostingServer;
    public static final Path FILE_DIRECTORY = Path.of("MMMMM/shared-files");

    public static void start() throws IOException {
        MMMMM.LOGGER.info("FileHostingServer.start() called with port: {}", Config.fileServerPort);

        if (fileHostingServer != null) {
            MMMMM.LOGGER.warn("File hosting server already running");
            return;
        }

        if (Config.fileServerPort <= 0) {
            MMMMM.LOGGER.error("Invalid port number: {}. Server will not start.", Config.fileServerPort);
            return;
        }

        if (!Files.exists(FILE_DIRECTORY)) {
            Files.createDirectories(FILE_DIRECTORY);
        }

        fileHostingServer = HttpServer.create(new InetSocketAddress(Config.fileServerPort), 0);

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

        new Thread(() -> {
            fileHostingServer.start();
            MMMMM.LOGGER.info("File hosting server started on port {}", fileHostingServer.getAddress().getPort());
        }).start();
    }

    public static void stop() {
        if (fileHostingServer != null) {
            fileHostingServer.stop(0);
            MMMMM.LOGGER.info("File hosting server stopped.");
        }
    }
}
