package com.mmmmm.client;

import com.mmmmm.core.Checksum;
import com.mmmmm.core.Config;
import com.mmmmm.core.MMMMM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
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
    private static final int MAX_CHANGE_LIST_ITEMS = 5;

    private static final class UpdateSummary {
        private final String title;
        private final List<String> lines;

        private UpdateSummary(String title, List<String> lines) {
            this.title = title;
            this.lines = lines;
        }
    }

    private static final class UpdateOutcome {
        private final boolean cancelled;
        private final boolean success;
        private final Checksum.ChecksumDiff diff;

        private UpdateOutcome(boolean cancelled, boolean success, Checksum.ChecksumDiff diff) {
            this.cancelled = cancelled;
            this.success = success;
            this.diff = diff;
        }

        private static UpdateOutcome cancelled() {
            return new UpdateOutcome(true, false, null);
        }

        private static UpdateOutcome success(Checksum.ChecksumDiff diff) {
            return new UpdateOutcome(false, true, diff);
        }

        private static UpdateOutcome failed() {
            return new UpdateOutcome(false, false, null);
        }

        private boolean isCancelled() {
            return cancelled;
        }

        private boolean isSuccess() {
            return success;
        }

        private Checksum.ChecksumDiff getDiff() {
            return diff;
        }

        private boolean hasChanges() {
            return diff != null && !diff.isEmpty();
        }
    }

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
                    String updateBaseUrl = ServerMetadata.getMetadata(server.ip); // IP or full URL
                    LOGGER.info("Update button clicked for server: {}", updateBaseUrl);
                    runUpdateForServer(updateBaseUrl);
                }
        ).bounds(x, y, 50, 20).build();
    }

    private static void runUpdateForServer(String updateBaseUrl) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen returnScreen = minecraft.screen;

        String modsUrl = buildDownloadUrl(updateBaseUrl, MOD_ZIP_NAME);
        if (modsUrl == null) {
            LOGGER.info("No mod URL found for {}", updateBaseUrl);
            return;
        }

        DownloadProgressScreen progressScreen = new DownloadProgressScreen("mods", modsUrl, returnScreen);
        minecraft.setScreen(progressScreen);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> performUpdateFlow(updateBaseUrl, modsUrl, minecraft, progressScreen, executor));
    }

    private static void performUpdateFlow(
            String updateBaseUrl,
            String modsUrl,
            Minecraft minecraft,
            DownloadProgressScreen progressScreen,
            ExecutorService executor
    ) {
        UpdateOutcome modsOutcome = UpdateOutcome.failed();
        UpdateOutcome configOutcome = Config.updateConfig ? UpdateOutcome.failed() : UpdateOutcome.success(null);
        boolean cancelled = false;

        try {
            LOGGER.info("Starting mod download from: {}", modsUrl);

            modsOutcome = downloadAndApplyZipUpdate(
                    minecraft,
                    progressScreen,
                    modsUrl,
                    "mods",
                    MOD_DOWNLOAD_PATH,
                    MOD_UNZIP_DESTINATION,
                    MOD_CHECKSUM_FILE,
                    true
            );

            if (modsOutcome.isCancelled()) {
                cancelled = true;
                return;
            }

            if (Config.updateConfig) {
                configOutcome = downloadConfigUpdate(updateBaseUrl, minecraft, progressScreen);
                if (configOutcome.isCancelled()) {
                    cancelled = true;
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to download or extract mods", e);
            UpdateSummary summary = buildUpdateSummary(updateBaseUrl, modsOutcome, configOutcome, true);
            minecraft.execute(() -> progressScreen.showSummary(summary.title, summary.lines));
            sendPlayerMessages(minecraft, summary.lines);
            return;
        } finally {
            executor.shutdown();
        }

        if (cancelled) {
            minecraft.execute(() -> progressScreen.showSummary("Update cancelled", List.of("Update cancelled by user.")));
            sendPlayerMessages(minecraft, List.of("Update cancelled by user."));
            return;
        }

        UpdateSummary summary = buildUpdateSummary(updateBaseUrl, modsOutcome, configOutcome, false);
        minecraft.execute(() -> progressScreen.showSummary(summary.title, summary.lines));
        sendPlayerMessages(minecraft, summary.lines);
    }

    private static UpdateOutcome downloadConfigUpdate(
            String updateBaseUrl,
            Minecraft minecraft,
            DownloadProgressScreen progressScreen
    ) {
        String configUrl = buildDownloadUrl(updateBaseUrl, CONFIG_ZIP_NAME);
        if (configUrl == null) {
            LOGGER.warn("Config update enabled but no config URL found for {}", updateBaseUrl);
            return UpdateOutcome.failed();
        }

        LOGGER.info("Starting config download from: {}", configUrl);
        try {
            return downloadAndApplyZipUpdate(
                    minecraft,
                    progressScreen,
                    configUrl,
                    "config",
                    CONFIG_DOWNLOAD_PATH,
                    CONFIG_UNZIP_DESTINATION,
                    CONFIG_CHECKSUM_FILE,
                    false
            );
        } catch (Exception e) {
            LOGGER.error("Failed to download or extract config", e);
            return UpdateOutcome.failed();
        }
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

    private static UpdateOutcome downloadAndApplyZipUpdate(
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
            return UpdateOutcome.cancelled();
        }

        minecraft.execute(() -> progressScreen.startProcessing("Preparing " + displayName + "...", "Validating download..."));
        validateDownloadedFile(downloadPath, displayName);
        prepareDestinationDirectory(unzipDestination);
        Set<String> extractedFiles = null;
        if (syncModsById) {
            LOGGER.info("Using modId sync extraction for {}", displayName);
            extractModsZipFileWithModIdSync(downloadPath, unzipDestination, progressScreen);
        } else {
            extractedFiles = extractZipFile(downloadPath, unzipDestination, progressScreen, displayName);
        }
        Checksum.ChecksumDiff diff = computeAndSaveChecksums(
                unzipDestination,
                checksumFile,
                progressScreen,
                displayName,
                extractedFiles
        );
        return UpdateOutcome.success(diff);
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

    private static Checksum.ChecksumDiff computeAndSaveChecksums(
            Path targetDirectory,
            Path checksumFile,
            DownloadProgressScreen progressScreen,
            String displayName,
            Set<String> filterFiles
    ) throws Exception {
        LOGGER.info("Comparing checksums...");
        updateProcessing(progressScreen, "Comparing " + displayName + " checksums...", "Scanning files...", 0, false);
        Checksum.ChecksumResult result = Checksum.compareChecksums(
                targetDirectory,
                checksumFile,
                (current, total, fileName) -> {
                    boolean hasTotal = total > 0;
                    int progress = hasTotal ? (int) ((current * 100L) / total) : 0;
                    String detail = hasTotal
                            ? String.format("%d/%d: %s", current, total, fileName)
                            : String.format("%d files... %s", current, fileName);
                    updateProcessing(progressScreen, "Comparing " + displayName + " checksums...", detail, progress, total > 0);
                }
        );
        Checksum.saveChecksums(checksumFile, result.getNewChecksums());
        Checksum.ChecksumDiff diff = result.getDiff();
        if (filterFiles != null && !filterFiles.isEmpty()) {
            diff = Checksum.filterDiff(diff, filterFiles);
        }
        return diff;
    }

    private static Set<String> extractZipFile(
            Path zipPath,
            Path destination,
            DownloadProgressScreen progressScreen,
            String displayName
    ) throws IOException {
        Set<String> extractedFiles = new HashSet<>();
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zipFile.entries());
            int total = entries.size();
            int current = 0;
            for (ZipEntry entry : entries) {
                current++;
                String entryName = entry.getName();
                int progress = total > 0 ? (int) ((current * 100L) / total) : 0;
                String detail = total > 0
                        ? String.format("%d/%d: %s", current, total, entryName)
                        : entryName;
                updateProcessing(progressScreen, "Extracting " + displayName + "...", detail, progress, total > 0);

                Path entryPath = destination.resolve(entryName).normalize();
                if (!entryPath.startsWith(destination)) {
                    throw new IOException("Blocked zip entry outside destination: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    extractedFiles.add(entryPath.getFileName().toString());
                }
            }
        }
        return extractedFiles;
    }

    private static void extractModsZipFileWithModIdSync(
            Path zipPath,
            Path destination,
            DownloadProgressScreen progressScreen
    ) throws Exception {
        Map<String, List<Path>> existingModsById = indexInstalledModsById(destination, progressScreen);
        LOGGER.info("Indexed {} modIds in {}", existingModsById.size(), destination);

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zipFile.entries());
            int total = entries.size();
            int current = 0;

            for (ZipEntry entry : entries) {
                current++;
                String entryName = entry.getName();
                Path entryPath = destination.resolve(entryName).normalize();
                if (!entryPath.startsWith(destination)) {
                    throw new IOException("Blocked zip entry outside destination: " + entryName);
                }

                int progress = total > 0 ? (int) ((current * 100L) / total) : 0;
                String detail = total > 0
                        ? String.format("%d/%d: %s", current, total, entryName)
                        : entryName;
                updateProcessing(progressScreen, "Extracting mods...", detail, progress, total > 0);

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

    private static Map<String, List<Path>> indexInstalledModsById(
            Path modsDirectory,
            DownloadProgressScreen progressScreen
    ) {
        Map<String, List<Path>> byId = new HashMap<>();
        if (!Files.exists(modsDirectory)) {
            return byId;
        }

        List<Path> jarPaths;
        try (var stream = Files.walk(modsDirectory)) {
            jarPaths = stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jar"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warn("Failed to index installed mods in {}", modsDirectory, e);
            return byId;
        }

        int total = jarPaths.size();
        int current = 0;
        for (Path jarPath : jarPaths) {
            current++;
            int progress = total > 0 ? (int) ((current * 100L) / total) : 0;
            String detail = total > 0
                    ? String.format("%d/%d: %s", current, total, jarPath.getFileName())
                    : jarPath.getFileName().toString();
            updateProcessing(progressScreen, "Indexing installed mods...", detail, progress, total > 0);

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

    private static UpdateSummary buildUpdateSummary(
            String updateBaseUrl,
            UpdateOutcome modsOutcome,
            UpdateOutcome configOutcome,
            boolean failedEarly
    ) {
        List<String> lines = new ArrayList<>();
        boolean configAttempted = Config.updateConfig;
        boolean modsSuccess = modsOutcome != null && modsOutcome.isSuccess();
        boolean configSuccess = !configAttempted || (configOutcome != null && configOutcome.isSuccess());
        boolean modsChanged = modsOutcome != null && modsOutcome.hasChanges();
        boolean configChanged = configAttempted && configOutcome != null && configOutcome.hasChanges();

        String title = failedEarly ? "Update failed" : "Update complete";

        if (modsSuccess) {
            lines.add(buildUpdateMessage("Mods", modsOutcome.getDiff()));
        } else {
            lines.add("Mods update failed for " + updateBaseUrl + ". Check logs for details.");
            title = "Update failed";
        }

        if (configAttempted) {
            if (configSuccess) {
                lines.add(buildUpdateMessage("Config", configOutcome.getDiff()));
            } else {
                lines.add("Config update failed for " + updateBaseUrl + ". Check logs for details.");
                title = "Update failed";
            }
        } else {
            lines.add("Config updates disabled.");
        }

        if (modsChanged) {
            lines.add("Mods were updated. Please restart the game to apply them.");
        } else if (!modsChanged && !configChanged && !failedEarly && modsSuccess && configSuccess) {
            lines.add("No updates found.");
        }

        return new UpdateSummary(title, lines);
    }

    private static String buildUpdateMessage(String label, Checksum.ChecksumDiff diff) {
        if (diff == null || diff.isEmpty()) {
            return label + ": no changes.";
        }

        String summary = label + " updated (added " + diff.getAdded().size()
                + ", modified " + diff.getModified().size()
                + ", removed " + diff.getRemoved().size()
                + ").";

        String details = buildChangeDetails(diff);
        return details.isEmpty() ? summary : summary + " " + details;
    }

    private static String buildChangeDetails(Checksum.ChecksumDiff diff) {
        List<String> sections = new ArrayList<>();

        String added = formatChangeSection("Added", diff.getAdded());
        if (!added.isEmpty()) {
            sections.add(added);
        }

        String modified = formatChangeSection("Modified", diff.getModified());
        if (!modified.isEmpty()) {
            sections.add(modified);
        }

        String removed = formatChangeSection("Removed", diff.getRemoved());
        if (!removed.isEmpty()) {
            sections.add(removed);
        }

        return sections.isEmpty() ? "" : String.join(" | ", sections);
    }

    private static String formatChangeSection(String label, List<String> items) {
        if (items.isEmpty()) {
            return "";
        }

        int showCount = Math.min(items.size(), MAX_CHANGE_LIST_ITEMS);
        List<String> shown = items.subList(0, showCount);
        String text = label + ": " + String.join(", ", shown);

        if (items.size() > showCount) {
            text += " (+" + (items.size() - showCount) + " more)";
        }

        return text;
    }

    private static void sendPlayerMessages(Minecraft minecraft, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        minecraft.execute(() -> {
            if (minecraft.player == null) {
                return;
            }
            for (String message : messages) {
                minecraft.player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    private static void updateProcessing(
            DownloadProgressScreen progressScreen,
            String title,
            String detail,
            int progress,
            boolean hasProgress
    ) {
        if (progressScreen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> progressScreen.updateProcessing(title, detail, progress, hasProgress));
    }
}
