package com.example.examplemod;

// Import statements will go here. For now, they are omitted for brevity.
import net.minecraft.util.HttpUtil;
import net.neoforged.api.distmarker.Dist;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.server.loading.ServerModLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Mod(ExampleMod.MODID) // Main class annotation to define this as a Mod entry point.
public class ExampleMod {

    // Define the mod ID (used as a namespace prefix throughout the mod).
    public static final String MODID = "examplemod";

    // Logger instance for debugging and information logging.
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Example constructor for the mod, where initialization logic is placed.
     * Called during mod loading. Automatically receives required parameters like event buses.
     */
    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register mod-specific setup events or logic here.
        LOGGER.info("Initializing ExampleMod...");

        // Example of registering a setup event directly.
        modEventBus.addListener(this::commonSetup);

        Path savePath = Path.of("MMMMM/download.zip");

        try {
            Files.createDirectories(savePath.getParent()); // Creates the MMMMM folder if it doesn't exist
        } catch (IOException e) {
            LOGGER.error("Failed to create MMMMM folder", e);
            return; // Exit if the folder cannot be created
        }
    }

    /**
     * Common setup method, which is used for initializing shared mod components.
     * This is called during a specific lifecycle phase set by Minecraft Forge.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Performing common setup for ExampleMod...");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("Player joined");

        Path savePath = Path.of("MMMMM/mods.zip");
        try {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(Config.packURL).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (InputStream inputStream = connection.getInputStream()) {
                            Files.copy(inputStream, savePath, StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info("File downloaded successfully to: " + savePath);
                        }
                    }
                } catch (java.net.MalformedURLException e) {
                    LOGGER.error("Invalid URL provided in config: {}", Config.packURL, e);
                } catch (java.io.IOException e) {
                    LOGGER.error("Failed to open a connection to the URL: {}", Config.packURL, e);
                }
                unzip(savePath, Path.of("MMMMM/mods"));
            });
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred during file download", e);
        }
    }

    /**
     * Example event listener for when the server is starting.
     * Events allow reactions to game states.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server is starting with ExampleMod loaded...");
    }

    /**
     * Static inner class for client-specific events.
     * This ensures client-specific code does not get executed on the server side.
     */
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        /**
         * Handles client setup logic, e.g., rendering or key bindings.
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Setting up client for ExampleMod...");
        }
    }
    private void unzip(Path zipFile, Path destDir) {
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path filePath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipIn, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipIn.closeEntry();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to unzip file {} to {}", zipFile, destDir, e);
        }
    }
}