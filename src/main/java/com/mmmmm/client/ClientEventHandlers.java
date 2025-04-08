package com.mmmmm.client;

import com.mmmmm.Config;
import com.mmmmm.MMMMM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent.Init.Post;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Handles client-side events for ExampleMod.
 */
@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/mods.zip");
    private static final Path UNZIP_DESTINATION = Path.of("mods");
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static final List<Button> serverButtons = new ArrayList<>();

    /**
     * Adds custom buttons for each server in the server list on the multiplayer screen.
     */
    @SubscribeEvent
    public static void onMultiplayerScreenInit(Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen screen) {
            LOGGER.info("Multiplayer screen initialized. Adding server buttons.");

            ServerList serverList = new ServerList(Minecraft.getInstance());
            serverList.load();

            int buttonX = screen.width - 160; // Position buttons on the right side
            int buttonY = 50;
            int buttonSpacing = 24;
            int maxHeight = screen.height - 50; // Keep buttons within screen height

            for (int i = 0; i < serverList.size(); i++) {
                int yOffset = buttonY + (i * buttonSpacing);
                if (yOffset + 20 > maxHeight) break; // Prevent buttons from going off-screen

                ServerData server = serverList.get(i);
                Button serverButton = Button.builder(
                        Component.literal("Update " + server.name),
                        (btn) -> {
                            LOGGER.info("Update button clicked for server: {}", server.name);
                            downloadAndProcessMod(server.name);
                        }
                ).bounds(buttonX, yOffset, 150, 20).build();

                event.addListener(serverButton);
                serverButtons.add(serverButton);
            }
        }
    }

    /**
     * Handles mod downloading and processing for a specific server.
     */
    private static void downloadAndProcessMod(String serverIP) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String modsUrl = "http://" + serverIP + "/mods.zip";
                LOGGER.info("Starting mod download from: {}", modsUrl);

                HttpURLConnection connection = initializeConnection(modsUrl);
                downloadFile(connection, MOD_DOWNLOAD_PATH);

                if (Files.size(MOD_DOWNLOAD_PATH) == 0) {
                    LOGGER.error("Downloaded ZIP file is empty: {}", MOD_DOWNLOAD_PATH);
                    throw new IOException("The downloaded mods file is empty!");
                }

                LOGGER.info("Downloaded ZIP file size: {} bytes", Files.size(MOD_DOWNLOAD_PATH));

                if (!Files.exists(UNZIP_DESTINATION)) {
                    Files.createDirectories(UNZIP_DESTINATION);
                }

                zipIn(MOD_DOWNLOAD_PATH, UNZIP_DESTINATION);
                sendPlayerMessage("Mods downloaded and extracted successfully for " + serverIP + "!");

            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods", e);
                sendPlayerMessage("Failed to download or extract mods for " + serverIP + ". Check logs for more details.");
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
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("File downloaded successfully to: {}", destination);
        }
    }

    private static void zipIn(Path zipFilePath, Path destinationPath) throws Exception {
        try (InputStream fileInputStream = Files.newInputStream(zipFilePath);
             java.util.zip.ZipInputStream zipInputStream = new java.util.zip.ZipInputStream(fileInputStream)) {

            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Remove the root directory from the entry name
                String entryName = entry.getName();
                if (entryName.contains("/")) {
                    entryName = entryName.substring(entryName.indexOf("/") + 1);
                }

                if (entryName.isEmpty()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                Path entryPath = destinationPath.resolve(entryName).normalize();
                if (!entryPath.startsWith(destinationPath)) {
                    throw new IOException("Invalid ZIP entry attempting to escape destination: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zipInputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
            LOGGER.info("ZIP file extracted successfully to: {}", destinationPath);
        }
    }

    private static void sendPlayerMessage(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}
