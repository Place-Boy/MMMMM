package com.example.examplemod;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main mod class for ExampleMod, with events safely separated into client and server logic.
 */
@Mod(ExampleMod.MODID)
public class ExampleMod {

    public static final String MODID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static HttpServer fileHostingServer; // File hosting server instance

    // Default configuration settings, overridable through the config file
    public static int FILE_SERVER_PORT = 8080;
    public static final Path FILE_DIRECTORY = Path.of("shared-files");

    /**
     * Constructor for the ExampleMod class.
     */
    public ExampleMod(ModContainer modContainer) {
        LOGGER.info("Initializing ExampleMod...");
        // Register configuration during initialization
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /**
     * Starts the file hosting server to serve files from the shared directory.
     */
    public void startFileHostingServer() throws IOException {
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
        LOGGER.info("File hosting server started on port " + FILE_SERVER_PORT);
    }

    /**
     * Stops the file hosting server.
     */
    public static void stopFileHostingServer() {
        if (fileHostingServer != null) {
            fileHostingServer.stop(0);
            LOGGER.info("File hosting server stopped.");
        }
    }
}

/**
 * Client-side event handlers for ExampleMod.
 */
@Mod(value = ExampleMod.MODID, dist = Dist.CLIENT)
class ClientEventHandlers {

    /**
     * Adds a custom button to the Title Screen.
     */
    @SubscribeEvent
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof TitleScreen titleScreen) {
            Button customButton = Button.builder(
                    Component.literal("Check for Updates"),
                            button -> ExampleMod.LOGGER.info("Update check triggered!"))
                    .pos(titleScreen.width / 2 - 100, titleScreen.height / 4 + 48) // Set position
                    .size(200, 20) // Set size
                    .build();
            titleScreen.renderables.add(customButton);
        }
    }
}

/**
 * Server-side event handlers for ExampleMod.
 */
@Mod(value = ExampleMod.MODID, dist = Dist.DEDICATED_SERVER)
class ServerEventHandlers {

    /**
     * Start the file hosting server when the server starts.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            ExampleMod.LOGGER.info("Starting the file hosting server...");
            new ExampleMod(null).startFileHostingServer();
        } catch (IOException e) {
            ExampleMod.LOGGER.error("Failed to start file hosting server: ", e);
        }
    }

    /**
     * Perform common setup, if necessary (can be extended in the future).
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        ExampleMod.LOGGER.info("Performing common setup tasks.");
    }
}