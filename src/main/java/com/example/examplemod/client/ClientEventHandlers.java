package com.example.examplemod.client;

import com.example.examplemod.Config;
import com.example.examplemod.ExampleMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent.Init.Post;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;

/**
 * Handles client-side events for ExampleMod.
 */
@EventBusSubscriber(modid = ExampleMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/mods.zip");
    private static final Path UNZIP_DESTINATION = Path.of("MMMMM/mods");
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static Button updateCheckButton;

    /**
     * Adds a custom button to the multiplayer screen.
     */
    @SubscribeEvent
    public static void onMultiplayerScreenInit(Post event) {  // Fixed function name
        if (event.getScreen() instanceof JoinMultiplayerScreen screen) {
            LOGGER.info("Multiplayer screen initialized. Adding button.");

            int screenWidth = screen.width;
            int screenHeight = screen.height;

            // Correct button positioning (centered)
            int newButtonX = screenWidth + Config.buttonX;
            int newButtonY = screenHeight + Config.buttonY;

            // Create the button
            updateCheckButton = Button.builder(
                    Component.literal("Check for updates"),
                    (btn) -> {
                        LOGGER.info("Update check button clicked!");
                        onButtonClicked(btn);
                    }
            ).bounds(newButtonX, newButtonY, 150, 20).build();

            // Properly add button to screen
            event.addListener(updateCheckButton);
            LOGGER.info("Button successfully added at ({}, {}).", newButtonX, newButtonY);
        }
    }

    /**
     * Logic to handle what happens when the button is clicked.
     */
    public static void onButtonClicked(Button button) {
        LOGGER.info("Update check button was pressed!");
        downloadAndProcessMod();
    }

    /**
     * Checks if the player is available in the current context.
     */
    private static boolean isPlayerAvailable() {
        return Minecraft.getInstance().player != null;
    }

    private static void downloadAndProcessMod() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String modsUrl = Config.packURL; // URL for mods ZIP file
                LOGGER.info("Starting mod download from: {}", modsUrl);

                // Initialize connection and download the file
                HttpURLConnection connection = initializeConnection(modsUrl);
                downloadFile(connection, MOD_DOWNLOAD_PATH);

                // Check if the ZIP file is valid (non-empty)
                if (Files.size(MOD_DOWNLOAD_PATH) == 0) {
                    LOGGER.error("Downloaded ZIP file is empty: {}", MOD_DOWNLOAD_PATH);
                    throw new IOException("The downloaded mods file is empty!");
                }
                LOGGER.info("Downloaded ZIP file size: {} bytes", Files.size(MOD_DOWNLOAD_PATH));

                // Proceed to extract the ZIP file
                if (!Files.exists(UNZIP_DESTINATION)) {
                    Files.createDirectories(UNZIP_DESTINATION);
                }
                zipIn(MOD_DOWNLOAD_PATH, UNZIP_DESTINATION);

                // Notify success
                sendPlayerMessage("Mods downloaded and extracted successfully!");

            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods", e);
                sendPlayerMessage("Failed to download or extract mods. Check logs for more details.");
            }
        });
    }

    private static HttpURLConnection initializeConnection(String url) throws Exception {
        URL downloadUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MS);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        LOGGER.info("Connecting to {} - Response Code: {}", url, responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to fetch mods - Server returned response code: " + responseCode);
        }

        return connection;
    }

    private static void downloadFile(HttpURLConnection connection, Path destination) throws Exception {
        try (InputStream in = connection.getInputStream()) {
            String contentType = connection.getHeaderField("Content-Type");

            // Accept both application/zip and application/octet-stream
            if (!"application/zip".equals(contentType) && !"application/octet-stream".equals(contentType)) {
                String errorPreview = new String(in.readNBytes(50)); // Read for debugging
                LOGGER.error("Unexpected Content-Type: {}. Preview of response: {}", contentType, errorPreview);
                throw new IOException("Unexpected Content-Type: " + contentType + ". Expected application/zip.");
            }

            // Proceed to download the file
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("File downloaded successfully to: {}", destination);
        }
    }

    private static void zipIn(Path zipFilePath, Path destinationPath) {
        try (InputStream fileInputStream = Files.newInputStream(zipFilePath);
             java.util.zip.ZipInputStream zipInputStream = new java.util.zip.ZipInputStream(fileInputStream)) {

            java.util.zip.ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Extract entry name and normalize
                Path entryPath = Path.of(entry.getName()).normalize();

                // If the entry has only one component (e.g., "mod1.jar"), use it directly
                if (entryPath.getNameCount() == 1) {
                    entryPath = entryPath.getFileName(); // Use just the file name
                } else if (entryPath.getNameCount() > 1) {
                    // If the entry has subpaths, adjust the path accordingly
                    entryPath = entryPath.subpath(1, entryPath.getNameCount());
                } else {
                    LOGGER.warn("Skipping invalid or empty entry: {}", entry.getName());
                    continue;
                }

                // Final file location
                Path newFilePath = destinationPath.resolve(entryPath).normalize();

                // Ensure the path doesn't escape the destination directory
                if (!newFilePath.startsWith(destinationPath)) {
                    throw new IOException("Invalid ZIP entry attempting to escape destination: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(newFilePath);
                } else {
                    Files.createDirectories(newFilePath.getParent());
                    Files.copy(zipInputStream, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                }

                zipInputStream.closeEntry();
            }

            LOGGER.info("ZIP file extracted successfully to: {}", destinationPath);

        } catch (Exception e) {
            LOGGER.error("Failed to extract ZIP file.", e);
            sendPlayerMessage("Failed to extract mods. Check logs for details.");
        }
    }


    /**
     * Sends a system message to the player.
     */
    private static void sendPlayerMessage(String message) {
        if (isPlayerAvailable()) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}
