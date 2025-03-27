package com.example.examplemod.server;

import com.example.examplemod.ExampleMod;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Manages the file hosting server for ExampleMod.
 */
public class FileHostingServer {

    private static HttpServer fileHostingServer;
    public static final int FILE_SERVER_PORT = 8080;
    public static final Path FILE_DIRECTORY = Path.of("MMMMM/shared-files");

    /**
     * Starts the file hosting server.
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
                // Log the incoming request URI
                String requestPath = exchange.getRequestURI().getPath();
                ExampleMod.LOGGER.info("Received request: " + requestPath);

                // Handle the root path ("/") request
                if ("/".equals(requestPath)) {
                    // Try serving a default index.html file
                    Path defaultFile = FILE_DIRECTORY.resolve("index.html").normalize();

                    if (Files.exists(defaultFile)) {
                        ExampleMod.LOGGER.info("Serving default file: " + defaultFile);
                        byte[] defaultFileBytes = Files.readAllBytes(defaultFile);
                        exchange.getResponseHeaders().add("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, defaultFileBytes.length);
                        exchange.getResponseBody().write(defaultFileBytes);
                    } else {
                        // Generate and serve a directory listing if index.html is not found
                        ExampleMod.LOGGER.info("Generating directory listing for root request.");
                        String fileList = Files.list(FILE_DIRECTORY)
                                .map(path -> "<li><a href=\"/" + path.getFileName() + "\">" + path.getFileName() + "</a></li>")
                                .collect(Collectors.joining());
                        String html = "<html><body><h1>Available Files</h1><ul>" + fileList + "</ul></body></html>";
                        byte[] htmlBytes = html.getBytes();
                        exchange.getResponseHeaders().add("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, htmlBytes.length);
                        exchange.getResponseBody().write(htmlBytes);
                    }
                    return; // Exit after handling root path
                }

                // Resolve the requested file path
                Path filePath = FILE_DIRECTORY.resolve(requestPath.substring(1)).normalize();

                // Security: Ensure the resolved file path is within the allowed directory
                if (!filePath.startsWith(FILE_DIRECTORY)) {
                    ExampleMod.LOGGER.warn("Unauthorized access attempt: " + filePath);
                    exchange.sendResponseHeaders(403, -1); // Forbidden
                    return;
                }

                // Check if the requested file exists
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    ExampleMod.LOGGER.warn("File not found: " + filePath);
                    exchange.sendResponseHeaders(404, -1); // Not Found
                    return;
                }

                // Read the file and write it to the HTTP response
                byte[] fileBytes = Files.readAllBytes(filePath);
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, fileBytes.length);
                exchange.getResponseBody().write(fileBytes);

                ExampleMod.LOGGER.info("Successfully served file: " + filePath);

            } catch (Exception e) {
                // Handle unexpected errors gracefully
                ExampleMod.LOGGER.error("Error processing request", e);
                try {
                    exchange.sendResponseHeaders(500, -1); // Internal Server Error
                } catch (IOException ioException) {
                    ExampleMod.LOGGER.error("Failed to send error response", ioException);
                }
            } finally {
                // Ensure the exchange is always closed to release resources
                exchange.close();
            }
        });

        // Start the server
        fileHostingServer.start();
        ExampleMod.LOGGER.info("File hosting server started on port " + FILE_SERVER_PORT);
    }

    /**
     * Stops the file hosting server.
     */
    public static void stop() {
        if (fileHostingServer != null) {
            fileHostingServer.stop(0);
            ExampleMod.LOGGER.info("File hosting server stopped.");
        }
    }
}