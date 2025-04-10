package com.mmmmm.client;

import com.mmmmm.Checksum;
import com.mmmmm.Config;
import com.mmmmm.MMMMM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles client-side events for ExampleMod.
 */
@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/shared-files/mods.zip");
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

            int buttonX = screen.width - 55; // Position buttons on the right side
            int buttonY = 50;
            int buttonSpacing = 24;
            int maxHeight = screen.height - 50; // Keep buttons within screen height

            for (int i = 0; i < serverList.size(); i++) {
                int yOffset = buttonY + (i * buttonSpacing);
                if (yOffset + 20 > maxHeight) break; // Prevent buttons from going off-screen

                ServerData server = serverList.get(i);
                Button serverButton = Button.builder(
                        Component.literal("Update"),
                        (btn) -> {
                            LOGGER.info("Update");
                            downloadAndProcessMod(server.name);
                        }
                ).bounds(buttonX, yOffset, 50, 20).build();

                event.addListener(serverButton);
                serverButtons.add(serverButton);
            }
        }
    }

    /**
     * Handles mod downloading and processing for a specific server.
     */
    private static final Path CHECKSUM_FILE = Path.of("MMMMM/mods_checksums.json");

    private static void downloadAndProcessMod(String serverIP) {
        Minecraft minecraft = Minecraft.getInstance();
        DownloadProgressScreen progressScreen = new DownloadProgressScreen(serverIP);
        TitleScreen titleScreen = new TitleScreen();
        minecraft.setScreen(progressScreen);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Check if serverIP already contains a port
                String modsUrl;
                if (serverIP.contains(":")) {
                    modsUrl = "http://" + serverIP + "/mods.zip";
                } else {
                    modsUrl = "http://" + serverIP + ":" + Config.fileServerPort + "/mods.zip";
                }
                LOGGER.info("Starting mod download from: {}", modsUrl);

                HttpURLConnection connection = initializeConnection(modsUrl);

                // Download the file with progress updates
                downloadFileWithProgress(connection, MOD_DOWNLOAD_PATH, progressScreen);

                // Verify the file exists
                if (!Files.exists(MOD_DOWNLOAD_PATH)) {
                    throw new IOException("Downloaded file not found: " + MOD_DOWNLOAD_PATH);
                }

                // Create destination directory if it doesn't exist
                if (!Files.exists(UNZIP_DESTINATION)) {
                    Files.createDirectories(UNZIP_DESTINATION);
                }

                // Compare checksums before extraction
                if (Files.exists(CHECKSUM_FILE)) {
                    LOGGER.info("Comparing checksums...");
                    Checksum.compareChecksums(UNZIP_DESTINATION, CHECKSUM_FILE);
                }

                // Extract the ZIP file
                zipIn(MOD_DOWNLOAD_PATH, UNZIP_DESTINATION);

                // Save updated checksums
                LOGGER.info("Saving updated checksums...");
                Checksum.saveChecksums(UNZIP_DESTINATION, CHECKSUM_FILE);

                sendPlayerMessage("Mods downloaded, verified, and extracted successfully for " + serverIP + "!");
            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods", e);
                sendPlayerMessage("Failed to download or extract mods for " + serverIP + ". Check logs for more details.");
            } finally {
                minecraft.execute(() -> minecraft.setScreen(titleScreen));
                Executors.newSingleThreadExecutor().shutdown();
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

    private static void downloadFileWithProgress(HttpURLConnection connection, Path destination, DownloadProgressScreen progressScreen) throws IOException {
        Files.createDirectories(destination.getParent());

        long totalBytes = connection.getContentLengthLong();
        java.util.concurrent.atomic.AtomicLong bytesRead = new java.util.concurrent.atomic.AtomicLong(0);
        long startTime = System.currentTimeMillis();
        long lastUpdateTime = startTime;
        long bytesReadInInterval = 0;

        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            Files.deleteIfExists(destination); // Ensure no leftover file
            try (var out = Files.newOutputStream(destination)) {
                while ((read = in.read(buffer)) != -1) {
                    if (progressScreen.isCancelled()) { // Check for cancellation
                        throw new IOException("Download cancelled by user.");
                    }

                    out.write(buffer, 0, read);
                    bytesRead.addAndGet(read);
                    bytesReadInInterval += read;

                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - lastUpdateTime;
                    if (elapsedTime >= 200) {
                        double speedInKB = (bytesReadInInterval / 1024.0) / (elapsedTime / 1000.0);
                        lastUpdateTime = currentTime;
                        bytesReadInInterval = 0;

                        String speedText = speedInKB >= 1024
                                ? String.format("%.2f MB/s", speedInKB / 1024.0)
                                : String.format("%.2f KB/s", speedInKB);

                        int progress = (int) ((bytesRead.get() * 100) / totalBytes);
                        Minecraft.getInstance().execute(() -> progressScreen.updateProgress(progress, speedText));
                    }
                }
            }
        } catch (Exception e) {
            // Delete the partially downloaded file if an error occurs
            Files.deleteIfExists(destination);
            throw e; // Re-throw the exception to handle it upstream
        }

        // Verify file size
        if (Files.size(destination) == 0) {
            Files.deleteIfExists(destination); // Delete empty file
            throw new IOException("Downloaded file is empty.");
        }
    }

    private static void zipIn(Path zipFilePath, Path destinationPath) throws Exception {
        try (InputStream fileInputStream = Files.newInputStream(zipFilePath);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            ZipEntry entry;
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