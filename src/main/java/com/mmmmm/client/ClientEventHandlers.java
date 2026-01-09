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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

@Mod.EventBusSubscriber(modid = MMMMM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandlers {

    private static final int CONNECTION_TIMEOUT_MS = 5000;

    // Downloads saved in shared-files subfolder
    private static final Path MODS_DOWNLOAD = Path.of("MMMMM/shared-files/mods.zip");
    private static final Path CONFIG_DOWNLOAD = Path.of("MMMMM/shared-files/config.zip");
    private static final Path KUBEJS_DOWNLOAD = Path.of("MMMMM/shared-files/kubejs.zip");

    // Unzip destinations
    private static final Path MODS_DEST = Path.of("mods");
    private static final Path CONFIG_DEST = Path.of("config");
    private static final Path KUBEJS_DEST = Path.of("kubejs");

    // Per-package checksum files
    private static final Path MODS_CHECKSUM = Path.of("MMMMM/mods_checksums.json");
    private static final Path CONFIG_CHECKSUM = Path.of("MMMMM/config_checksums.json");
    private static final Path KUBEJS_CHECKSUM = Path.of("MMMMM/kubejs_checksums.json");

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static final List<Button> serverButtons = new ArrayList<>();

    private static record PackageTask(String name, Path downloadPath, Path unzipDestination, Path checksumFile) {}

    @SubscribeEvent
    public static void onMultiplayerScreenInit(ScreenEvent.Init.Post event) {
        LOGGER.info("onMultiplayerScreenInit fired"); // At start of onMultiplayerScreenInit
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

        List<Button> buttonsToAdd = new ArrayList<>();


        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerData server = serverList.get(i);
            Button serverButton = createServerButton(buttonX, yOffset, server);
            buttonsToAdd.add(serverButton);
            LOGGER.info("Button added to screen: {}", event.getScreen().getClass().getName());
        }
        // Add all buttons last, after all other widgets
        for (Button button : buttonsToAdd) {
            LOGGER.info("Button added to screen: {}", event.getScreen().getClass().getName());
        }
    }

    public static Button createServerButton(int x, int y, ServerData server) {
        LOGGER.info("Creating button for server: {}", server.ip); // In createServerButton
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

        String baseUrl = serverUpdateIP;
        if (baseUrl == null || baseUrl.isBlank()) {
            LOGGER.info("No update URL found for " + serverUpdateIP);
            return;
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://" + baseUrl;
        }

        final String finalBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        DownloadProgressScreen progressScreen = new DownloadProgressScreen(finalBaseUrl);
        minecraft.setScreen(progressScreen);

        Executors.newSingleThreadExecutor().execute(() -> {
            List<String> successes = new ArrayList<>();
            List<String> failures = new ArrayList<>();

            List<PackageTask> tasks = List.of(
                    new PackageTask("mods", MODS_DOWNLOAD, MODS_DEST, MODS_CHECKSUM),
                    new PackageTask("config", CONFIG_DOWNLOAD, CONFIG_DEST, CONFIG_CHECKSUM),
                    new PackageTask("kubejs", KUBEJS_DOWNLOAD, KUBEJS_DEST, KUBEJS_CHECKSUM)
            );

            try {
                for (PackageTask task : tasks) {
                    String packageUrl = finalBaseUrl + "/" + task.name + ".zip";
                    try {
                        LOGGER.info("Starting download for {} from {}", task.name, packageUrl);
                        // update UI to show which package is processing
                        minecraft.execute(() -> minecraft.setScreen(new DownloadProgressScreen(packageUrl)));

                        HttpURLConnection connection = initializeConnection(packageUrl);
                        downloadFileWithProgress(connection, task.downloadPath, progressScreen);

                        validateDownloadedFile(task.downloadPath);
                        prepareDestinationDirectory(task.unzipDestination);
                        compareChecksumsIfExists(task.unzipDestination, task.checksumFile);
                        extractZipFile(task.downloadPath, task.unzipDestination);
                        saveUpdatedChecksums(task.unzipDestination, task.checksumFile);

                        successes.add(task.name);
                        LOGGER.info("Successfully processed {}", task.name);
                    } catch (Exception e) {
                        LOGGER.error("Failed processing " + task.name, e);
                        failures.add(task.name);
                        // continue with next package
                    }
                }

                if (!failures.isEmpty()) {
                    sendPlayerMessage("Update completed with errors. Succeeded: " + successes + " Failed: " + failures);
                } else {
                    sendPlayerMessage("All packages downloaded, verified, and extracted: " + successes);
                }
            } finally {
                minecraft.execute(() -> minecraft.setScreen(titleScreen));
                Executors.newSingleThreadExecutor().shutdown();
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
            throw new IOException("Failed to fetch package - Server returned response code: " + responseCode);
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
                    Files.deleteIfExists(destination); // Delete the partially downloaded file
                    return;
                }

                out.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;

                int progress = totalBytes > 0 ? (int) ((downloadedBytes * 100) / totalBytes) : 0;
                long elapsedTime = System.currentTimeMillis() - startTime;
                double speedInKB = elapsedTime > 0 ? (downloadedBytes / 1024.0) / (elapsedTime / 1000.0) : 0.0;

                String speed = speedInKB >= 1024
                        ? String.format("%.2f MB/s", speedInKB / 1024)
                        : String.format("%.2f KB/s", speedInKB);

                progressScreen.updateProgress(progress, speed);
            }
        }
    }

    private static void validateDownloadedFile(Path downloadPath) throws IOException {
        if (!Files.exists(downloadPath) || Files.size(downloadPath) == 0) {
            throw new IOException("Downloaded file is invalid or empty: " + downloadPath);
        }
    }

    private static void prepareDestinationDirectory(Path unzipDestination) throws IOException {
        if (!Files.exists(unzipDestination)) {
            Files.createDirectories(unzipDestination);
        }
    }

    private static void compareChecksumsIfExists(Path unzipDestination, Path checksumFile) throws Exception {
        if (Files.exists(checksumFile)) {
            LOGGER.info("Comparing checksums for {} using {}", unzipDestination, checksumFile);
            Checksum.compareChecksums(unzipDestination, checksumFile);
        }
    }

    private static void extractZipFile(Path zipFilePath, Path unzipDestination) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(zipFilePath);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = unzipDestination.resolve(entry.getName()).normalize();
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

    private static void saveUpdatedChecksums(Path unzipDestination, Path checksumFile) throws Exception {
        LOGGER.info("Saving updated checksums for {} -> {}", unzipDestination, checksumFile);
        Checksum.saveChecksums(unzipDestination, checksumFile);
    }

    private static void sendPlayerMessage(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}
