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
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.moandjiezana.toml.Toml;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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

        var executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
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
                        MOD_CHECKSUM_FILE,
                        true
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
                                    CONFIG_CHECKSUM_FILE,
                                    false
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
                executor.shutdown();
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
            Path checksumFile,
            boolean syncModsById
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
        if (syncModsById) {
            LOGGER.info("Using modId sync extraction for {}", displayName);
            extractModsZipFileWithModIdSync(downloadPath, unzipDestination);
        } else {
            extractZipFile(downloadPath, unzipDestination);
        }
        compareChecksumsIfExists(unzipDestination, checksumFile);
        saveUpdatedChecksums(unzipDestination, checksumFile);
        return true;
    }

    private static HttpURLConnection initializeConnection(String url, String displayName) throws IOException {
        URL downloadUrl;
        try {
            downloadUrl = URI.create(url).toURL();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL: " + url, e);
        }
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
            boolean hasLength = totalBytes > 0;
            long downloadedBytes = 0;
            long startTime = System.currentTimeMillis();

            byte[] buffer = new byte[8192];
            int bytesRead;
            String lastSpeed = "0 KB/s";

            while ((bytesRead = in.read(buffer)) != -1) {
                if (progressScreen.isCancelled()) {
                    LOGGER.info("Download cancelled by user.");
                    return;
                }

                out.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;

                int progress = hasLength ? (int) ((downloadedBytes * 100) / totalBytes) : 0;
                long elapsedTime = System.currentTimeMillis() - startTime;
                double speedInKB = elapsedTime > 0 ? (downloadedBytes / 1024.0) / (elapsedTime / 1000.0) : 0.0;

                String speed = speedInKB >= 1024
                        ? String.format("%.2f MB/s", speedInKB / 1024)
                        : String.format("%.2f KB/s", speedInKB);
                lastSpeed = speed;

                // Calculate ETA
                long bytesRemaining = totalBytes - downloadedBytes;
                String eta;
                if (!hasLength) {
                    eta = "Unknown";
                } else {
                    double secondsRemaining = (speedInKB > 0) ? (bytesRemaining / 1024.0) / speedInKB : 0.0;
                    if (secondsRemaining > 0) {
                        int minutes = (int) (secondsRemaining / 60);
                        int seconds = (int) (secondsRemaining % 60);
                        eta = String.format("%dm %ds", minutes, seconds);
                    } else {
                        eta = "Calculating...";
                    }
                }

                progressScreen.updateProgress(progress, speed, eta);
            }

            if (!hasLength) {
                progressScreen.updateProgress(100, lastSpeed, "");
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
                if (!entryPath.startsWith(destination)) {
                    throw new IOException("Blocked zip entry outside destination: " + entry.getName());
                }
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

    private static void extractModsZipFileWithModIdSync(Path zipPath, Path destination) throws Exception {
        Map<String, List<Path>> existingModsById = indexInstalledModsById(destination);
        LOGGER.info("Indexed {} modIds in {}", existingModsById.size(), destination);

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                Path entryPath = destination.resolve(entryName).normalize();
                if (!entryPath.startsWith(destination)) {
                    throw new IOException("Blocked zip entry outside destination: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    continue;
                }

                Files.createDirectories(entryPath.getParent());

                boolean isJar = entryName.toLowerCase().endsWith(".jar");
                if (isJar) {
                    byte[] jarBytes;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        jarBytes = is.readAllBytes();
                    }

                    try {
                        Set<String> modIds = getModIdsFromJarBytes(jarBytes);
                        if (modIds.isEmpty()) {
                            LOGGER.warn("Could not identify modId for {} - extracting without duplicate cleanup.", entryName);
                        } else {
                            LOGGER.info("Zip entry {} has modId(s): {}", entryName, String.join(", ", modIds));
                            for (String modId : modIds) {
                                if (modId == null || modId.isBlank()) {
                                    continue;
                                }
                                // Remove any installed jar with the same modId (including jars extracted earlier in this run),
                                // except the current target file name.
                                List<Path> installed = existingModsById.getOrDefault(modId, Collections.emptyList());
                                for (Path installedJar : installed) {
                                    if (installedJar.equals(entryPath)) {
                                        continue;
                                    }
                                    try {
                                        if (Files.deleteIfExists(installedJar)) {
                                            LOGGER.info("Removed old mod jar for modId {}: {}", modId, installedJar.getFileName());
                                        }
                                    } catch (Exception e) {
                                        LOGGER.warn("Failed to remove old mod jar {} for modId {}", installedJar, modId, e);
                                    }
                                }

                                // Track the jar we're about to write so later duplicates (if any) can replace it.
                                existingModsById.put(modId, new ArrayList<>(List.of(entryPath)));
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to identify modId for {} - extracting without duplicate cleanup.", entryName, e);
                    }

                    Files.write(entryPath, jarBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static Map<String, List<Path>> indexInstalledModsById(Path modsDirectory) {
        Map<String, List<Path>> byId = new HashMap<>();
        if (!Files.exists(modsDirectory)) {
            return byId;
        }

        try (var stream = Files.walk(modsDirectory)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jar"))
                    .forEach(jarPath -> {
                        try {
                            Set<String> modIds = getModIdsFromJarFile(jarPath);
                            for (String modId : modIds) {
                                if (modId == null || modId.isBlank()) {
                                    continue;
                                }
                                byId.computeIfAbsent(modId, k -> new ArrayList<>()).add(jarPath);
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed to read modId from installed jar: {}", jarPath, e);
                        }
                    });
        } catch (Exception e) {
            LOGGER.warn("Failed to index installed mods in {}", modsDirectory, e);
        }

        return byId;
    }

    private static Set<String> getModIdsFromJarFile(Path jarPath) throws Exception {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) {
                entry = zipFile.getEntry("META-INF/mods.toml");
            }
            if (entry == null) {
                return Collections.emptySet();
            }

            try (InputStream is = zipFile.getInputStream(entry)) {
                Toml toml = new Toml().read(is);
                return extractModIdsFromToml(toml);
            }
        }
    }

    private static Set<String> getModIdsFromJarBytes(byte[] jarBytes) throws Exception {
        try (ZipInputStream jarZip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry jarEntry;
            while ((jarEntry = jarZip.getNextEntry()) != null) {
                String name = jarEntry.getName();
                if (!jarEntry.isDirectory()
                        && ("META-INF/neoforge.mods.toml".equals(name) || "META-INF/mods.toml".equals(name))) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    jarZip.transferTo(baos);
                    Toml toml = new Toml().read(new ByteArrayInputStream(baos.toByteArray()));
                    return extractModIdsFromToml(toml);
                }
                jarZip.closeEntry();
            }
        }
        return Collections.emptySet();
    }

    private static Set<String> extractModIdsFromToml(Toml toml) {
        if (toml == null) {
            return Collections.emptySet();
        }

        Set<String> modIds = new HashSet<>();
        List<Toml> modsTables = toml.getTables("mods");
        if (modsTables != null) {
            for (Toml modTable : modsTables) {
                String modId = modTable.getString("modId");
                if (modId != null && !modId.isBlank()) {
                    modIds.add(modId.toLowerCase(Locale.ROOT));
                }
            }
        }

        return modIds;
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
