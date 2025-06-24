package com.mmmmm.server;

import com.mmmmm.Config;
import com.mmmmm.MMMMM;
import com.sun.net.httpserver.HttpServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.MOD)
public class FileHostingServer {

    private static HttpServer fileHostingServer;
    public static final Path FILE_DIRECTORY = Path.of("MMMMM/shared-files");

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (!event.getConfig().getSpec().equals(Config.SPEC)) return;

        Config.fileServerPort = Config.FILE_SERVER_PORT.get();
        MMMMM.LOGGER.info("Config loaded - fileServerPort = {}", Config.fileServerPort);

        try {
            FileHostingServer.start();
            MMMMM.LOGGER.info("Called FileHostingServer.start()");
        } catch (IOException e) {
            MMMMM.LOGGER.error("Failed to start file hosting server: ", e);
        }
    }

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
            // Your existing request handling code...
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
