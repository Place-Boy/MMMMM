package com.mmmmm.client;

import com.mmmmm.Checksum;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    private static final int CONNECTION_TIMEOUT_MS = 5000;

    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/shared-files/mods.zip");
    private static final Path CONFIG_DOWNLOAD_PATH = Path.of("MMMMM/shared-files/config.zip");
    private static final Path KUBEJS_DOWNLOAD_PATH = Path.of("MMMMM/shared-files/kubejs.zip");

    private static final Path UNZIP_DESTINATION = Path.of("mods");
    private static final Path CONFIG_UNZIP_DESTINATION = Path.of("config");
    private static final Path KUBEJS_UNZIP_DESTINATION = Path.of("kubejs");

    private static final Path CHECKSUM_FILE = Path.of("MMMMM/mods_checksums.json");
    private static final Path CONFIG_CHECKSUM_FILE = Path.of("MMMMM/config_checksums.json");
    private static final Path KUBEJS_CHECKSUM_FILE = Path.of("MMMMM/kubejs_checksums.json");

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static final List<Button> serverButtons = new ArrayList<>();

    // Reuse a single executor for download tasks instead of creating per-call executors
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

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

        if (serverUpdateIP == null || serverUpdateIP.isBlank()) {
            LOGGER.info("No update URL found for server.");
            return;
        }

        String baseUrl = normalizeBaseUrl(serverUpdateIP);

        DownloadProgressScreen progressScreen = new DownloadProgressScreen(baseUrl, (downloadConfig, downloadKubejs, downloadMods, screen) -> {
            DOWNLOAD_EXECUTOR.execute(() -> {
                try {
                    if (downloadMods) {
                        LOGGER.info("Starting mods download from base URL: {}", baseUrl);
                        downloadAndProcessPackage(baseUrl, "mods", MOD_DOWNLOAD_PATH, UNZIP_DESTINATION,
                                CHECKSUM_FILE, "mods", screen, true);
                    }

                    if (downloadConfig) {
                        LOGGER.info("Starting config download (if available) from base URL: {}", baseUrl);
                        downloadAndProcessPackage(baseUrl, "config", CONFIG_DOWNLOAD_PATH, CONFIG_UNZIP_DESTINATION,
                                CONFIG_CHECKSUM_FILE, "config", screen, false);
                    }

                    if (downloadKubejs) {
                        LOGGER.info("Starting kubejs download (if available) from base URL: {}", baseUrl);
                        downloadAndProcessPackage(baseUrl, "kubejs", KUBEJS_DOWNLOAD_PATH, KUBEJS_UNZIP_DESTINATION,
                                KUBEJS_CHECKSUM_FILE, "kubejs", screen, false);
                    }

                    sendPlayerMessage("Selected content downloaded, verified, and extracted for " + serverUpdateIP + "!");
                } catch (Exception e) {
                    LOGGER.error("Failed to download or extract one or more packages", e);
                    sendPlayerMessage("Failed to download or extract required files for " + serverUpdateIP + ". Check logs for more details.");
                } finally {
                    minecraft.execute(() -> minecraft.setScreen(titleScreen));
                }
            });
        });

        minecraft.setScreen(progressScreen);
    }

    private static String normalizeBaseUrl(String serverUpdateIP) {
        String url = serverUpdateIP.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        // Ensure no trailing slash so we can safely append /name.zip
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static void downloadAndProcessPackage(String baseUrl,
                                                  String packageName,
                                                  Path downloadPath,
                                                  Path unzipDestination,
                                                  Path checksumFile,
                                                  String extractionLabel,
                                                  DownloadProgressScreen progressScreen,
                                                  boolean required) throws Exception {
        String url = baseUrl + "/" + packageName + ".zip";
        LOGGER.info("Preparing to download {} from {}", packageName, url);

        HttpURLConnection connection;
        try {
            connection = initializeConnection(url);
        } catch (IOException e) {
            if (!required) {
                LOGGER.warn("Optional package '{}' not available at {}: {}", packageName, url, e.toString());
                return;
            }
            throw e;
        }

        // Download
        minecraftSafeUpdate(progressScreen, () -> progressScreen.startExtraction("Downloading " + extractionLabel + "..."));
        downloadFileWithProgress(connection, downloadPath, progressScreen);

        // Validate and extract
        minecraftSafeUpdate(progressScreen, () -> progressScreen.startExtraction("Extracting " + extractionLabel + "..."));
        validateDownloadedFile(downloadPath);
        prepareDestinationDirectory(unzipDestination);
        compareChecksumsIfExists(unzipDestination, checksumFile);
        extractZipFile(downloadPath, unzipDestination);
        saveUpdatedChecksums(unzipDestination, checksumFile);
    }

    private static HttpURLConnection initializeConnection(String url) throws IOException {
        URL downloadUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download file. HTTP response code: " + responseCode + " from URL: " + url);
        }

        return connection;
    }

    private static void downloadFileWithProgress(HttpURLConnection connection,
                                                 Path destination,
                                                 DownloadProgressScreen progressScreen) throws IOException {
        long contentLength = connection.getContentLengthLong();
        if (contentLength <= 0) {
            contentLength = -1; // unknown
        }

        long startTime = System.nanoTime();

        try (InputStream in = connection.getInputStream()) {
            Files.createDirectories(destination.getParent());

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int read;

            try (var out = Files.newOutputStream(destination)) {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;

                    long elapsedNanos = System.nanoTime() - startTime;
                    if (elapsedNanos <= 0) elapsedNanos = 1; // avoid div by zero
                    double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

                    // Progress percent
                    final int percent;
                    if (contentLength > 0) {
                        percent = (int) ((totalRead * 100) / contentLength);
                    } else {
                        // Fallback when content length is unknown: use a fake percent based on chunks
                        percent = Math.min(99, (int) (elapsedSeconds * 10));
                    }
                    final int clampedPercent = Math.min(100, Math.max(0, percent));

                    // Download speed (bytes/sec -> KB/s or MB/s)
                    double bytesPerSecond = totalRead / elapsedSeconds;
                    final String speedString;
                    if (bytesPerSecond >= 1024 * 1024) {
                        speedString = String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024));
                    } else if (bytesPerSecond >= 1024) {
                        speedString = String.format("%.2f KB/s", bytesPerSecond / 1024);
                    } else {
                        speedString = String.format("%.0f B/s", bytesPerSecond);
                    }

                    // ETA
                    final String etaString;
                    if (contentLength > 0 && bytesPerSecond > 0) {
                        double remainingBytes = contentLength - totalRead;
                        double remainingSeconds = remainingBytes / bytesPerSecond;
                        int etaSeconds = (int) Math.round(remainingSeconds);
                        int minutes = etaSeconds / 60;
                        int seconds = etaSeconds % 60;
                        if (minutes > 0) {
                            etaString = String.format("%dm %02ds", minutes, seconds);
                        } else {
                            etaString = String.format("%ds", seconds);
                        }
                    } else {
                        etaString = "--";
                    }

                    Minecraft.getInstance().execute(() ->
                            progressScreen.updateProgress(clampedPercent, speedString, etaString)
                    );
                }
            }

            if (contentLength > 0 && totalRead != contentLength) {
                LOGGER.warn("Downloaded size ({}) does not match expected content length ({}).", totalRead, contentLength);
            }
        }
    }

    private static void minecraftSafeUpdate(DownloadProgressScreen screen, Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(action);
    }

    private static void validateDownloadedFile(Path downloadPath) throws IOException {
        if (!Files.exists(downloadPath) || Files.size(downloadPath) == 0) {
            throw new IOException("Downloaded file is invalid or empty: " + downloadPath);
        }
    }

    private static void prepareDestinationDirectory(Path destination) throws IOException {
        if (!Files.exists(destination)) {
            Files.createDirectories(destination);
        }
    }

    private static void compareChecksumsIfExists(Path destination, Path checksumFile) throws Exception {
        if (Files.exists(checksumFile)) {
            LOGGER.info("Comparing checksums for {} using {}...", destination, checksumFile);
            Checksum.compareChecksums(destination, checksumFile);
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

    private static void saveUpdatedChecksums(Path destination, Path checksumFile) throws Exception {
        LOGGER.info("Saving updated checksums for {} to {}...", destination, checksumFile);
        Checksum.saveChecksums(destination, checksumFile);
    }

    private static void sendPlayerMessage(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}

