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

@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final String MOD_ZIP_NAME = "mods.zip";
    private static final String CONFIG_ZIP_NAME = "config.zip";
    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/shared-files", MOD_ZIP_NAME);
    private static final Path MOD_UNZIP_DESTINATION = Path.of("mods");
    private static final Path MOD_CHECKSUM_FILE = Path.of("MMMMM/mods_checksums.json");
    private static final Path CONFIG_DOWNLOAD_PATH = Path.of("MMMMM/shared-files", CONFIG_ZIP_NAME);
    private static final Path CONFIG_UNZIP_DESTINATION = Path.of("config");
    private static final Path CONFIG_CHECKSUM_FILE = Path.of("MMMMM/config_checksums.json");
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static final List<Button> serverButtons = new ArrayList<>();

    @SubscribeEvent
    public static void onMultiplayerScreenInit(Post event) {
        if (!(event.getScreen() instanceof JoinMultiplayerScreen screen)) {
            return;
        }

        LOGGER.info("Multiplayer screen initialized. Adding server buttons.");
        ServerList serverList = new ServerList(Minecraft.getInstance());
        serverList.load();

        int buttonX = screen.width - 55;
        int buttonY = 50;
        int buttonSpacing = 24;
        int maxHeight = screen.height - 50;

        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerData server = serverList.get(i);
            Button serverButton = createServerButton(buttonX, yOffset, server);
            event.addListener(serverButton);
            serverButtons.add(serverButton);
        }
    }

    private static Button createServerButton(int x, int y, ServerData server) {
        return Button.builder(
                Component.literal("Update"),
                (btn) -> {
                    String serverUpdateIP = ServerMetadata.getMetadata(server.ip); // Retrieve custom data (e.g., URL or IP)
                    LOGGER.info("Update button clicked for server: {}", serverUpdateIP);
                    downloadAndProcessMod(serverUpdateIP); // Pass the correct metadata
                }
        ).bounds(x, y, 50, 20).build();
    }

    private static void downloadAndProcessMod(String serverUpdateIP) {
        Minecraft minecraft = Minecraft.getInstance();
        TitleScreen titleScreen = new TitleScreen();

        String modsUrl = buildDownloadUrl(serverUpdateIP, MOD_ZIP_NAME);
        if (modsUrl == null) {
            LOGGER.info("No mod URL found for {}", serverUpdateIP);
            return;
        }

        DownloadProgressScreen progressScreen = new DownloadProgressScreen("mods", modsUrl);
        minecraft.setScreen(progressScreen);

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean modsUpdated = false;
            boolean configUpdated = false;
            boolean cancelled = false;

            try {
                LOGGER.info("Starting mod download from: {}", modsUrl);

                modsUpdated = downloadAndProcessZip(
                        minecraft,
                        progressScreen,
                        modsUrl,
                        "mods",
                        MOD_DOWNLOAD_PATH,
                        MOD_UNZIP_DESTINATION,
                        MOD_CHECKSUM_FILE
                );

                if (!modsUpdated) {
                    cancelled = progressScreen.isCancelled();
                    return;
                }

                if (Config.updateConfig) {
                    String configUrl = buildDownloadUrl(serverUpdateIP, CONFIG_ZIP_NAME);
                    if (configUrl == null) {
                        LOGGER.warn("Config update enabled but no config URL found for {}", serverUpdateIP);
                    } else {
                        LOGGER.info("Starting config download from: {}", configUrl);
                        try {
                            configUpdated = downloadAndProcessZip(
                                    minecraft,
                                    progressScreen,
                                    configUrl,
                                    "config",
                                    CONFIG_DOWNLOAD_PATH,
                                    CONFIG_UNZIP_DESTINATION,
                                    CONFIG_CHECKSUM_FILE
                            );
                            if (!configUpdated) {
                                cancelled = progressScreen.isCancelled();
                                return;
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to download or extract config", e);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods", e);
                sendPlayerMessage("Failed to download or extract mods for " + serverUpdateIP + ". Check logs for more details.");
                return;
            } finally {
                minecraft.execute(() -> minecraft.setScreen(titleScreen));
                Executors.newSingleThreadExecutor().shutdown();
            }

            if (cancelled) {
                sendPlayerMessage("Update cancelled.");
                return;
            }

            if (Config.updateConfig) {
                if (configUpdated) {
                    sendPlayerMessage("Mods and config downloaded, verified, and extracted successfully for " + serverUpdateIP + "!");
                } else {
                    sendPlayerMessage("Mods updated for " + serverUpdateIP + ", but config update failed. Check logs for more details.");
                }
            } else {
                sendPlayerMessage("Mods downloaded, verified, and extracted successfully for " + serverUpdateIP + "!");
            }
        });
    }

    private static String buildDownloadUrl(String serverUpdateIP, String zipFileName) {
        if (serverUpdateIP == null || serverUpdateIP.isBlank()) {
            return null;
        }

        String baseUrl = serverUpdateIP;
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://" + baseUrl;
        }

        String expectedSuffix = "/" + zipFileName;
        if (baseUrl.endsWith(expectedSuffix)) {
            return baseUrl;
        }

        int lastSlash = baseUrl.lastIndexOf('/');
        if (lastSlash > baseUrl.indexOf("://") + 2) {
            String lastSegment = baseUrl.substring(lastSlash + 1);
            if (lastSegment.endsWith(".zip")) {
                baseUrl = baseUrl.substring(0, lastSlash);
            }
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl + zipFileName;
        }

        return baseUrl + expectedSuffix;
    }

    private static boolean downloadAndProcessZip(
            Minecraft minecraft,
            DownloadProgressScreen progressScreen,
            String downloadUrl,
            String displayName,
            Path downloadPath,
            Path unzipDestination,
            Path checksumFile
    ) throws Exception {
        minecraft.execute(() -> progressScreen.startNewDownload(displayName, downloadUrl));

        HttpURLConnection connection = initializeConnection(downloadUrl, displayName);
        downloadFileWithProgress(connection, downloadPath, progressScreen);

        if (progressScreen.isCancelled()) {
            LOGGER.info("{} download cancelled by user.", displayName);
            return false;
        }

        minecraft.execute(() -> progressScreen.startExtraction("Extracting " + displayName + "..."));
        validateDownloadedFile(downloadPath, displayName);
        prepareDestinationDirectory(unzipDestination);
        compareChecksumsIfExists(unzipDestination, checksumFile);
        extractZipFile(downloadPath, unzipDestination);
        saveUpdatedChecksums(unzipDestination, checksumFile);
        return true;
    }

    private static HttpURLConnection initializeConnection(String url, String displayName) throws IOException {
        URL downloadUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MS);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        LOGGER.info("Connecting to {} - Response Code: {}", url, responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to fetch " + displayName + " - Server returned response code: " + responseCode);
        }

        return connection;
    }

    private static void downloadFileWithProgress(HttpURLConnection connection, Path destination, DownloadProgressScreen progressScreen) throws IOException {
        Files.createDirectories(destination.getParent());
        if (!Files.exists(destination)) {
            Files.createFile(destination); // Create the file only if it doesn't exist
        }
        try (InputStream in = connection.getInputStream();
             var out = Files.newOutputStream(destination)) {
            long totalBytes = connection.getContentLengthLong();
            long downloadedBytes = 0;
            long startTime = System.currentTimeMillis();

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (progressScreen.isCancelled()) {
                    LOGGER.info("Download cancelled by user.");
                    return;
                }

                out.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;

                int progress = (int) ((downloadedBytes * 100) / totalBytes);
                long elapsedTime = System.currentTimeMillis() - startTime;
                double speedInKB = elapsedTime > 0 ? (downloadedBytes / 1024.0) / (elapsedTime / 1000.0) : 0.0;

                String speed = speedInKB >= 1024
                        ? String.format("%.2f MB/s", speedInKB / 1024)
                        : String.format("%.2f KB/s", speedInKB);

                // Calculate ETA
                long bytesRemaining = totalBytes - downloadedBytes;
                double secondsRemaining = (speedInKB > 0) ? (bytesRemaining / 1024.0) / speedInKB : 0.0;
                String eta;
                if (secondsRemaining > 0) {
                    int minutes = (int) (secondsRemaining / 60);
                    int seconds = (int) (secondsRemaining % 60);
                    eta = String.format("%dm %ds", minutes, seconds);
                } else {
                    eta = "Calculating...";
                }

                progressScreen.updateProgress(progress, speed, eta);
            }
        }
    }

    private static void validateDownloadedFile(Path downloadPath, String displayName) throws IOException {
        if (!Files.exists(downloadPath) || Files.size(downloadPath) == 0) {
            throw new IOException("Downloaded " + displayName + " file is invalid or empty.");
        }
    }

    private static void prepareDestinationDirectory(Path destination) throws IOException {
        if (!Files.exists(destination)) {
            Files.createDirectories(destination);
        }
    }

    private static void compareChecksumsIfExists(Path targetDirectory, Path checksumFile) throws Exception {
        if (Files.exists(checksumFile)) {
            LOGGER.info("Comparing checksums...");
            Checksum.compareChecksums(targetDirectory, checksumFile);
        }
    }

    private static void extractZipFile(Path zipPath, Path destination) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = destination.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zipInputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static void saveUpdatedChecksums(Path targetDirectory, Path checksumFile) throws Exception {
        LOGGER.info("Saving updated checksums...");
        Checksum.saveChecksums(targetDirectory, checksumFile);
    }

    private static void sendPlayerMessage(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}
