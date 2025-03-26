package com.example.examplemod.server;

import com.example.examplemod.ExampleMod;
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
    public static final int FILE_SERVER_PORT = 8080;
    public static final Path FILE_DIRECTORY = Path.of("MMMMM/shared-files");

    /**
     * Starts the file hosting server.
     */
    public static void start() throws IOException {
        if (!Files.exists(FILE_DIRECTORY)) {
            Files.createDirectories(FILE_DIRECTORY);
        }
        fileHostingServer = HttpServer.create(new InetSocketAddress(FILE_SERVER_PORT), 0);
        fileHostingServer.createContext("/", exchange -> {
            Path filePath = FILE_DIRECTORY.resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
            if (!filePath.startsWith(FILE_DIRECTORY)) {
                exchange.sendResponseHeaders(403, -1); // Forbidden
                return;
            }
            if (Files.exists(filePath)) {
                byte[] fileBytes = Files.readAllBytes(filePath);
                exchange.sendResponseHeaders(200, fileBytes.length);
                exchange.getResponseBody().write(fileBytes);
            } else {
                exchange.sendResponseHeaders(404, -1); // Not Found
            }
            exchange.close();
        });
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
