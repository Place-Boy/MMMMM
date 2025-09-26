package com.mmmmm.client;

import com.mmmmm.Checksum;
import com.mmmmm.mixin.MultiplayerScreenAccessor;
import com.mmmmm.mixin.ScreenInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ClientEventHandlers implements ClientModInitializer {
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/shared-files/mods.zip");
    private static final Path UNZIP_DESTINATION = Path.of("mods");
    private static final Path CHECKSUM_FILE = Path.of("MMMMM/mods_checksums.json");
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static Screen lastScreen = null;

    @Override
    public void onInitializeClient() {
        // Fallback: poll in a thread since ClientTickEvents is not available
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                MinecraftClient client = MinecraftClient.getInstance();
                Screen current = client.currentScreen;
                if (current instanceof MultiplayerScreen screen && lastScreen != screen) {
                    lastScreen = screen;
                } else if (!(current instanceof MultiplayerScreen)) {
                    lastScreen = null;
                }
            }
        }, "mmmmm-multiplayer-poll").start();
    }



    private static ButtonWidget createServerButton(int x, int y, ServerInfo server) {
        return ButtonWidget.builder(
                Text.literal("Update"),
                (btn) -> {
                    String serverUpdateIP = ServerMetadata.getMetadata(server.address);
                    LOGGER.info("Update button clicked for server: {}", serverUpdateIP);
                    downloadAndProcessMod(serverUpdateIP);
                }
        ).dimensions(x, y, 50, 20).build();
    }

    public static void downloadAndProcessMod(String serverUpdateIP) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        TitleScreen titleScreen = new TitleScreen();

        String modsUrl = serverUpdateIP;
        // Default to HTTPS, fall back to HTTP if HTTPS fails
        if (modsUrl == null || modsUrl.isBlank()) {
            LOGGER.info("No mod URL found for " + serverUpdateIP);
            return;
        }

        String httpsUrl, httpUrl;
        if (modsUrl.startsWith("http://") || modsUrl.startsWith("https://")) {
            if (!modsUrl.endsWith("/mods.zip")) {
                modsUrl += "/mods.zip";
            }
            httpsUrl = modsUrl.startsWith("https://") ? modsUrl : null;
            httpUrl = modsUrl.startsWith("http://") ? modsUrl : null;
        } else {
            httpsUrl = "https://" + modsUrl + "/mods.zip";
            httpUrl = "http://" + modsUrl + "/mods.zip";
        }

        DownloadProgressScreen progressScreen = new DownloadProgressScreen(modsUrl);
        minecraft.setScreen(progressScreen);

        Executors.newSingleThreadExecutor().execute(() -> {
            String attemptedUrl = httpsUrl != null ? httpsUrl : httpUrl;
            boolean triedHttps = false;
            boolean triedHttp = false;
            try {
                HttpURLConnection connection = null;
                IOException lastException = null;
                // Try HTTPS first if available
                if (httpsUrl != null) {
                    attemptedUrl = httpsUrl;
                    triedHttps = true;
                    try {
                        LOGGER.info("Starting mod download from (HTTPS): {}", httpsUrl);
                        connection = initializeConnection(httpsUrl);
                    } catch (IOException e) {
                        LOGGER.warn("HTTPS download failed, will try HTTP: {}", e.getMessage());
                        lastException = e;
                    }
                }
                // If HTTPS failed, try HTTP
                if (connection == null && httpUrl != null) {
                    attemptedUrl = httpUrl;
                    triedHttp = true;
                    try {
                        LOGGER.info("Starting mod download from (HTTP): {}", httpUrl);
                        connection = initializeConnection(httpUrl);
                    } catch (IOException e) {
                        lastException = e;
                    }
                }
                if (connection == null) {
                    throw new IOException("Failed to connect to mod download URL via HTTPS and HTTP", lastException);
                }

                downloadFileWithProgress(connection, MOD_DOWNLOAD_PATH, progressScreen);
                validateDownloadedFile();
                prepareDestinationDirectory();
                compareChecksumsIfExists();
                extractZipFile();
                saveUpdatedChecksums();

                String protocolMsg = triedHttps && !triedHttp ? "HTTPS" : (triedHttp ? "HTTP" : "");
                sendPlayerMessage("Mods downloaded, verified, and extracted successfully for " + serverUpdateIP + "! (" + protocolMsg + ")");
            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods from " + attemptedUrl, e);
                sendPlayerMessage("Failed to download or extract mods for " + serverUpdateIP + ". Check logs for more details.");
                cleanupTempFiles(UNZIP_DESTINATION); // Cleanup leftover .tmp files
            } finally {
                minecraft.execute(() -> minecraft.setScreen(titleScreen));
            }
        });
    }

    private static HttpURLConnection initializeConnection(String url) throws IOException {
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
        if (!Files.exists(destination)) {
            Files.createFile(destination);
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

                progressScreen.updateProgress(progress, speed);
            }
        }
    }

    private static void validateDownloadedFile() throws IOException {
        if (!Files.exists(MOD_DOWNLOAD_PATH) || Files.size(MOD_DOWNLOAD_PATH) == 0) {
            throw new IOException("Downloaded file is invalid or empty.");
        }
    }

    private static void prepareDestinationDirectory() throws IOException {
        if (!Files.exists(UNZIP_DESTINATION)) {
            Files.createDirectories(UNZIP_DESTINATION);
        }
    }

    private static void compareChecksumsIfExists() throws Exception {
        if (Files.exists(CHECKSUM_FILE)) {
            LOGGER.info("Comparing checksums...");
            Checksum.compareChecksums(UNZIP_DESTINATION, CHECKSUM_FILE);
        }
    }

    private static void extractZipFile() throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(MOD_DOWNLOAD_PATH);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = UNZIP_DESTINATION.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    // Write to a temp file first to avoid locking issues
                    Path tempFile = Files.createTempFile(entryPath.getParent(), entryPath.getFileName().toString(), ".tmp");
                    try (OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                    // Atomically move temp file to destination, with retry for Windows lock issues
                    boolean moved = false;
                    int attempts = 0;
                    IOException lastException = null;
                    while (!moved && attempts < 5) {
                        try {
                            Files.move(tempFile, entryPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                            moved = true;
                        } catch (IOException ex) {
                            lastException = ex;
                            attempts++;
                            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        }
                    }
                    if (!moved) {
                        // Cleanup temp file if move fails
                        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                        throw new IOException("Failed to move temp file to destination after retries: " + entryPath, lastException);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    // Cleanup leftover .tmp files in the mods directory
    private static void cleanupTempFiles(Path directory) {
        try {
            Files.walk(directory)
                .filter(p -> p.getFileName().toString().endsWith(".tmp"))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        LOGGER.info("Deleted leftover temp file: {}", p);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to delete temp file: {}", p);
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up temp files in {}", directory);
        }
    }

    private static void saveUpdatedChecksums() throws Exception {
        LOGGER.info("Saving updated checksums...");
        Checksum.saveChecksums(UNZIP_DESTINATION, CHECKSUM_FILE);
    }

    private static void sendPlayerMessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
        }
    }
}