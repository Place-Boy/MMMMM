package com.mmmmm;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MMMMM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RegisterCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCommands.class);

    // Use a single executor across runs (avoid spinning threads every command)
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // Cache Modrinth lookups by mod name -> isServerOnly
    private static final Map<String, Boolean> MODRINTH_CACHE = new HashMap<>();

    // Track last build time to avoid unnecessary zips
    private static FileTime lastModsZipBuildTime = FileTime.fromMillis(0);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering server commands...");

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mmmmm")
                .then(Commands.literal("save-mods")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveModsToZip();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Zipping mods... check console for progress."),
                                    true
                            );
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("mmmmm")
                .then(Commands.literal("save-all")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveAllToZip();
                            context.getSource().sendSuccess(() -> Component.literal("Mods, config and kubejs have been saved to separate zip files in the shared-files directory"), true);
                            return 1;
                        })
                )
        );
    }

    public static void saveAllToZip(){
        EXECUTOR.execute(() -> {
            saveModsToZip();
            saveFolderToZip(Path.of("config"), Path.of("MMMMM", "shared-files", "config.zip"));
            saveFolderToZip(Path.of("kubejs"), Path.of("MMMMM", "shared-files", "kubejs.zip"));
        });
    }

    private static void saveFolderToZip(Path sourceFolder, Path targetZip) {
        try {
            Path parent = targetZip.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(targetZip))) {
                addFolderToZip(sourceFolder, sourceFolder, zipOut);
                LOGGER.info("Successfully created {} in shared-files.", targetZip.getFileName());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create " + targetZip.getFileName(), e);
        }
    }

    public static void addFolderToZip(Path rootFolder, Path sourceFolder, ZipOutputStream zipOut) throws IOException {
        if (!Files.exists(sourceFolder)) {
            LOGGER.info("Source folder does not exist, skipping: " + sourceFolder);
            return;
        }

        Files.walk(sourceFolder)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Path relativePath = rootFolder.relativize(path);
                        String zipEntryName = relativePath.toString().replace('\\', '/');
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
                        zipOut.putNextEntry(zipEntry);
                        Files.copy(path, zipOut);
                        zipOut.closeEntry();
                    } catch (IOException e) {
                        LOGGER.error("Failed to add file to zip: " + path, e);
                    }
                });
    }

    public static void saveModsToZip() {
        EXECUTOR.execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM", "shared-files", "mods.zip");

            try {
                if (!Files.exists(modsFolder)) {
                    LOGGER.warn("Mods folder does not exist, skipping zip.");
                    return;
                }

                List<Path> modFiles = Files.walk(modsFolder)
                        .filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".jar"))
                        .collect(Collectors.toList());

                if (modFiles.isEmpty()) {
                    LOGGER.warn("No .jar files found in mods folder, skipping zip.");
                    return;
                }

                FileTime latestChange = modFiles.stream()
                        .map(path -> {
                            try {
                                return Files.getLastModifiedTime(path);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        })
                        .max(FileTime::compareTo)
                        .orElse(FileTime.fromMillis(0));

                if (latestChange.compareTo(lastModsZipBuildTime) <= 0 && Files.exists(modsZip)) {
                    LOGGER.info("Mods have not changed since last build. Skipping zip creation.");
                    return;
                }

                Path parent = modsZip.getParent();
                if (parent != null) Files.createDirectories(parent);

                LOGGER.info("Starting mods.zip creation. Found {} mods.", modFiles.size());

                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                    int total = modFiles.size();
                    int index = 0;

                    for (Path path : modFiles) {
                        index++;
                        try {
                            String modName = getModNameFromJar(path);

                            boolean exclude = false;
                            if (Config.enableModFiltering && Config.filterServerSideMods && modName != null) {
                                exclude = isServerOnlyMod(modName);
                            }

                            if (!exclude) {
                                Path relativePath = modsFolder.relativize(path);
                                String zipEntryName = relativePath.toString().replace('\\', '/');
                                ZipEntry zipEntry = new ZipEntry(zipEntryName);
                                zipOut.putNextEntry(zipEntry);
                                Files.copy(path, zipOut);
                                zipOut.closeEntry();

                                LOGGER.info("[{}/{}] Included mod: {} ({})", index, total, modName, path.getFileName());
                            } else {
                                LOGGER.info("[{}/{}] Excluded mod: {} ({})", index, total, modName, path.getFileName());
                            }

                            if (modName == null) {
                                LOGGER.warn("[{}/{}] Could not identify mod name for: {}", index, total, path.getFileName());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to process mod: " + path, e);
                        }
                    }
                }

                lastModsZipBuildTime = latestChange;
                LOGGER.info("Finished creating mods.zip in shared-files. {} mods processed.", modFiles.size());
            } catch (IOException e) {
                LOGGER.error("Failed to create mods.zip", e);
            }
        });
    }

    private static String getModNameFromJar(Path jarPath) {
        // Forge typically uses META-INF/mods.toml
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/mods.toml");
            if (entry == null) {
                // Fallback: keep compatibility if a jar ships neoforge file too
                entry = zipFile.getEntry("META-INF/neoforge.mods.toml");
            }

            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    String toml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                    // Best-effort TOML parsing without adding deps:
                    // 1) root displayName/display_name
                    String root = extractTomlString(toml, "displayName");
                    if (root != null) return root;
                    root = extractTomlString(toml, "display_name");
                    if (root != null) return root;

                    // 2) first [[mods]] displayName
                    String firstMod = extractFirstModsDisplayName(toml);
                    if (firstMod != null) return firstMod;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read mods.toml from: {}, using file name as fallback.", jarPath, e);
            return jarPath.getFileName().toString();
        }

        return jarPath.getFileName().toString();
    }

    private static String extractTomlString(String toml, String key) {
        // best-effort: match key = "value"
        int idx = toml.indexOf(key);
        if (idx < 0) return null;

        int eq = toml.indexOf('=', idx + key.length());
        if (eq < 0) return null;

        int q1 = toml.indexOf('"', eq);
        if (q1 < 0) return null;

        int q2 = toml.indexOf('"', q1 + 1);
        if (q2 < 0) return null;

        String v = toml.substring(q1 + 1, q2).trim();
        return v.isEmpty() ? null : v;
    }

    private static String extractFirstModsDisplayName(String toml) {
        // Find first [[mods]] block, then find displayName within that block (until next [[...]] or EOF)
        int block = toml.indexOf("[[mods]]");
        if (block < 0) return null;

        int next = toml.indexOf("[[", block + 8);
        String section = (next >= 0) ? toml.substring(block, next) : toml.substring(block);

        String v = extractTomlString(section, "displayName");
        if (v != null) return v;

        // Some packs might use display_name even in lists
        return extractTomlString(section, "display_name");
    }

    private static boolean isServerOnlyMod(String modName) {
        Boolean cached = MODRINTH_CACHE.get(modName);
        if (cached != null) return cached;

        try {
            String query = URLEncoder.encode(modName, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/search?query=" + query))
                    .header("User-Agent", "Place-Boy/https://github.com/Place-Boy/MMMMM/1.0.2-beta")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();

            // Best-effort JSON parsing without deps:
            // Take first hit's client_side/server_side fields.
            // We only need string values of "client_side" and "server_side".
            int hitsIdx = body.indexOf("\"hits\"");
            if (hitsIdx >= 0) {
                int firstObj = body.indexOf('{', hitsIdx);
                if (firstObj >= 0) {
                    String clientSide = extractJsonStringField(body, "client_side");
                    String serverSide = extractJsonStringField(body, "server_side");

                    if (clientSide == null) clientSide = "required";
                    if (serverSide == null) serverSide = "required";

                    boolean result = "unsupported".equalsIgnoreCase(clientSide) && "required".equalsIgnoreCase(serverSide);
                    MODRINTH_CACHE.put(modName, result);
                    return result;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query Modrinth for: " + modName, e);
        }

        MODRINTH_CACHE.put(modName, false);
        return false;
    }

    private static String extractJsonStringField(String json, String fieldName) {
        if (json == null) return null;
        String needle = "\"" + fieldName + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;

        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) return null;

        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;

        int i = firstQuote + 1;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (esc) { sb.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        String v = sb.toString().trim();
        return v.isEmpty() ? null : v;
    }
}
