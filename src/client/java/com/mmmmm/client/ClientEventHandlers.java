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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                extractZipFile();
                updateChecksumsAndDeleteOutdated();
                sendPlayerMessage("Mods downloaded, verified, and extracted successfully for " + serverUpdateIP + "!");
            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods from " + attemptedUrl, e);
                sendPlayerMessage("Failed to download or extract mods for " + serverUpdateIP + ". Check logs for more details.");
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
        // Download to a temporary file first to avoid truncating the target (which could be read by the server)
        Path temp = destination.resolveSibling(destination.getFileName().toString() + ".downloading");
        // Ensure parent exists
        Files.createDirectories(destination.getParent());
        try (InputStream in = connection.getInputStream();
             var out = Files.newOutputStream(temp)) {
            long totalBytes = connection.getContentLengthLong();
            long downloadedBytes = 0;
            long startTime = System.currentTimeMillis();

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (progressScreen.isCancelled()) {
                    LOGGER.info("Download cancelled by user.");
                    // Clean up partial download
                    try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                    return;
                }

                out.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;

                int progress = 0;
                if (totalBytes > 0) {
                    progress = (int) ((downloadedBytes * 100) / totalBytes);
                }
                long elapsedTime = System.currentTimeMillis() - startTime;
                double speedInKB = elapsedTime > 0 ? (downloadedBytes / 1024.0) / (elapsedTime / 1000.0) : 0.0;

                String speed = speedInKB >= 1024
                        ? String.format("%.2f MB/s", speedInKB / 1024)
                        : String.format("%.2f KB/s", speedInKB);

                progressScreen.updateProgress(progress, speed);
            }
            // Finished writing temp file — atomically move to destination
            LOGGER.info("Download finished: wrote {} bytes to temporary file {} (content-length={})", downloadedBytes, temp, totalBytes);
            if (downloadedBytes == 0) {
                // Clean up empty temp file and fail fast
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                throw new IOException("Downloaded file is empty (0 bytes)");
            }
        }
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Moved downloaded file {} -> {}", temp, destination);
        } catch (AtomicMoveNotSupportedException amnse) {
            // Fallback to non-atomic move
            LOGGER.warn("Atomic move not supported, falling back to non-atomic move for {} -> {}", temp, destination);
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Moved downloaded file {} -> {} (non-atomic)", temp, destination);
        } catch (IOException ioe) {
            LOGGER.error("Failed to move downloaded temp file {} to final destination {}", temp, destination, ioe);
            // Attempt to clean up temp on failure
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw ioe;
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

    // Store the set of extracted mod file names for deletion logic
    private static final java.util.Set<String> extractedModFiles = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static void extractZipFile() throws IOException {
        extractedModFiles.clear();
        try (InputStream fileInputStream = Files.newInputStream(MOD_DOWNLOAD_PATH);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = UNZIP_DESTINATION.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream out = Files.newOutputStream(entryPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    } catch (java.nio.file.AccessDeniedException ade) {
                        LOGGER.warn("Access denied when writing {}. Scheduling for deletion on JVM exit.", entryPath);
                        sendPlayerMessage("File locked: " + entryPath.getFileName() + ". Will be deleted on exit.");
                        try {
                            forceDeleteOnExit(entryPath.toFile());
                        } catch (IOException ioe) {
                            LOGGER.error("Failed to schedule {} for deletion on exit: {}", entryPath, ioe.getMessage());
                        }
                        // Continue to next entry
                    }
                    // Track only .jar files for deletion logic
                    if (entryPath.getFileName().toString().endsWith(".jar")) {
                        extractedModFiles.add(entryPath.getFileName().toString());
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static void updateChecksumsAndDeleteOutdated() throws Exception {
        // Remove files in mods folder not present in extractedModFiles
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(UNZIP_DESTINATION, "*.jar")) {
            for (Path modFile : stream) {
                String fileName = modFile.getFileName().toString();
                if (!extractedModFiles.contains(fileName)) {
                    try {
                        if (Files.deleteIfExists(modFile)) {
                            LOGGER.info("Deleted outdated mod: {}", fileName);
                        }
                    } catch (FileSystemException fse) {
                        LOGGER.warn("Could not delete {} because it is locked ({}). Scheduling for deletion on exit.", fileName, fse.getMessage());
                        try {
                            forceDeleteOnExit(modFile.toFile());
                        } catch (IOException ioe) {
                            LOGGER.error("Failed to schedule {} for deletion on exit", fileName, ioe);
                        }
                    } catch (IOException ioe) {
                        LOGGER.error("Unexpected error deleting outdated mod {}", fileName, ioe);
                    }
                }
            }
        }
        // Save new checksums for all current files
        Checksum.saveChecksums(UNZIP_DESTINATION, CHECKSUM_FILE);
    }

    /**
     * Schedules a file or directory (recursively) for deletion on JVM exit.
     * @param file file or directory to delete, must not be null
     * @throws NullPointerException if the file is null
     * @throws IOException if scheduling deletion is unsuccessful
     */
    public static void forceDeleteOnExit(File file) throws IOException {
        if (file == null) throw new NullPointerException("File must not be null");
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    forceDeleteOnExit(child);
                }
            }
        }
        file.deleteOnExit();
    }

    private static void sendPlayerMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getServer() != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
