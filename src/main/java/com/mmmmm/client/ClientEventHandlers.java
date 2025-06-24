package com.mmmmm.client;

import com.mmmmm.Checksum;
import com.mmmmm.MMMMM;
import com.mmmmm.mixin.ScreenAccessorMixin;
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
    private static final Path MOD_DOWNLOAD_PATH = Path.of("MMMMM/shared-files/mods.zip");
    private static final Path UNZIP_DESTINATION = Path.of("mods");
    private static final Path CHECKSUM_FILE = Path.of("MMMMM/mods_checksums.json");
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandlers.class);
    private static final List<Button> serverButtons = new ArrayList<>();

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

        String modsUrl = serverUpdateIP;
        if (modsUrl == null || modsUrl.isBlank()) {
            LOGGER.info("No mod URL found for " + serverUpdateIP);
            return;
        }

        if (!modsUrl.startsWith("http://") && !modsUrl.startsWith("https://")) {
            modsUrl = "http://" + modsUrl;
        }

        if (!modsUrl.endsWith("/mods.zip")) {
            modsUrl += "/mods.zip";
        }

        final String finalModsUrl = modsUrl;

        DownloadProgressScreen progressScreen = new DownloadProgressScreen(modsUrl);
        minecraft.setScreen(progressScreen);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                LOGGER.info("Starting mod download from: {}", finalModsUrl);

                HttpURLConnection connection = initializeConnection(finalModsUrl);
                downloadFileWithProgress(connection, MOD_DOWNLOAD_PATH, progressScreen);

                validateDownloadedFile();
                prepareDestinationDirectory();
                compareChecksumsIfExists();
                extractZipFile();
                saveUpdatedChecksums();

                sendPlayerMessage("Mods downloaded, verified, and extracted successfully for " + serverUpdateIP + "!");
            } catch (Exception e) {
                LOGGER.error("Failed to download or extract mods", e);
                sendPlayerMessage("Failed to download or extract mods for " + serverUpdateIP + ". Check logs for more details.");
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
                    Files.copy(zipInputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static void saveUpdatedChecksums() throws Exception {
        LOGGER.info("Saving updated checksums...");
        Checksum.saveChecksums(UNZIP_DESTINATION, CHECKSUM_FILE);
    }

    private static void sendPlayerMessage(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}