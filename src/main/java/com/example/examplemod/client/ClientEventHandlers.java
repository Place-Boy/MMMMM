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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        try {
            Executors.newSingleThreadExecutor().execute(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = initializeConnection(Config.packURL);
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        downloadFile(connection, MOD_DOWNLOAD_PATH);
                        LOGGER.info("File downloaded successfully to: " + MOD_DOWNLOAD_PATH);
                    } else {
                        LOGGER.warn("Unexpected response code: " + connection.getResponseCode());
                    }
                } catch (MalformedURLException e) {
                    LOGGER.error("Invalid URL in config: {}", Config.packURL, e);
                } catch (Exception e) {
                    LOGGER.error("Error during file download", e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                zipIn(MOD_DOWNLOAD_PATH, UNZIP_DESTINATION);
            });
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred in download executor", e);
        }
    }

    private static HttpURLConnection initializeConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        return connection;
    }

    private static void downloadFile(HttpURLConnection connection, Path destination) throws Exception {
        try (InputStream inputStream = connection.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void zipIn(Path zipFilePath, Path destinationPath) {
        // Method implementation assumed to already exist
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
