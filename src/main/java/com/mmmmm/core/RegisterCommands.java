package com.mmmmm.core;

import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

public class RegisterCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCommands.class);

    // Use a single executor across runs
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Cache Modrinth lookups
    private static final Map<String, Boolean> modrinthCache = new HashMap<>();

    // Track last build time to avoid unnecessary zips
    private static FileTime lastBuildTime = FileTime.fromMillis(0);
    private static FileTime lastConfigBuildTime = FileTime.fromMillis(0);

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
                .then(Commands.literal("save-config")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveConfigToZip();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Zipping config... check console for progress."),
                                    true
                            );
                            return 1;
                        })
                )
        );
    }

    public static void saveModsToZip() {
        executor.execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");

            try {
                // Get list of .jar mods
                List<Path> modFiles;
                try (var stream = Files.walk(modsFolder)) {
                    modFiles = stream
                            .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
                            .collect(Collectors.toList());
                }

                if (modFiles.isEmpty()) {
                    LOGGER.warn("No .jar files found in mods folder, skipping zip.");
                    return;
                }

                // Check if mods changed since last zip build
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

                if (latestChange.compareTo(lastBuildTime) <= 0 && Files.exists(modsZip)) {
                    LOGGER.info("Mods have not changed since last build. Skipping zip creation.");
                    return;
                }

                LOGGER.info("Starting mods.zip creation. Found {} mods.", modFiles.size());

                // Ensure parent directories exist for mods.zip
                try {
                    Path parent = modsZip.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to create directories for mods.zip", e);
                    return;
                }

                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                    int total = modFiles.size();
                    int index = 0;

                    for (Path path : modFiles) {
                        index++;
                        try {
                            String modName = getModNameFromJar(path);
                            if (modName != null) {
                                boolean exclude = Config.filterServerSideMods && isServerOnlyMod(modName);
                                if (!exclude) {
                                    Path relativePath = modsFolder.relativize(path);
                                    ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                    zipOut.putNextEntry(zipEntry);
                                    Files.copy(path, zipOut);
                                    zipOut.closeEntry();

                                    LOGGER.info("[{}/{}] Included mod: {} ({})",
                                            index, total, modName, path.getFileName());
                                } else {
                                    LOGGER.info("[{}/{}] Excluded mod: {} ({})",
                                            index, total, modName, path.getFileName());
                                }
                            } else {
                                LOGGER.warn("[{}/{}] Could not identify mod: {}",
                                        index, total, path.getFileName());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to process mod: " + path, e);
                        }
                    }
                }

                lastBuildTime = latestChange;
                LOGGER.info("Finished creating mods.zip in shared-files. {} mods processed.", modFiles.size());

            } catch (IOException e) {
                LOGGER.error("Failed to create mods.zip", e);
            }
        });
    }

    public static void saveConfigToZip() {
        executor.execute(() -> {
            Path configFolder = Path.of("config");
            Path configZip = Path.of("MMMMM/shared-files/config.zip");

            try {
                if (!Files.exists(configFolder)) {
                    LOGGER.warn("Config folder does not exist, skipping zip.");
                    return;
                }

                List<Path> configFiles;
                try (var stream = Files.walk(configFolder)) {
                    configFiles = stream
                            .filter(Files::isRegularFile)
                            .collect(Collectors.toList());
                }

                if (configFiles.isEmpty()) {
                    LOGGER.warn("No files found in config folder, skipping zip.");
                    return;
                }

                FileTime latestChange = configFiles.stream()
                        .map(path -> {
                            try {
                                return Files.getLastModifiedTime(path);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        })
                        .max(FileTime::compareTo)
                        .orElse(FileTime.fromMillis(0));

                if (latestChange.compareTo(lastConfigBuildTime) <= 0 && Files.exists(configZip)) {
                    LOGGER.info("Config has not changed since last build. Skipping zip creation.");
                    return;
                }

                LOGGER.info("Starting config.zip creation. Found {} files.", configFiles.size());

                try {
                    Path parent = configZip.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to create directories for config.zip", e);
                    return;
                }

                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(configZip))) {
                    int total = configFiles.size();
                    int index = 0;

                    for (Path path : configFiles) {
                        index++;
                        Path relativePath = configFolder.relativize(path);
                        String entryName = relativePath.toString().replace('\\', '/');
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zipOut.putNextEntry(zipEntry);
                        Files.copy(path, zipOut);
                        zipOut.closeEntry();

                        LOGGER.info("[{}/{}] Included config file: {}",
                                index, total, relativePath);
                    }
                }

                lastConfigBuildTime = latestChange;
                LOGGER.info("Finished creating config.zip in shared-files. {} files processed.", configFiles.size());

            } catch (IOException e) {
                LOGGER.error("Failed to create config.zip", e);
            }
        });
    }

    private static String getModNameFromJar(Path jarPath) {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/neoforge.mods.toml");
            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    Toml toml = new Toml().read(is);
                    String rootDisplayName = toml.getString("display_name");
                    if (rootDisplayName != null) return rootDisplayName;
                    var modsList = toml.getTables("mods");
                    if (modsList != null && !modsList.isEmpty()) {
                        Toml firstMod = modsList.get(0);
                        String modDisplayName = firstMod.getString("displayName");
                        if (modDisplayName != null) return modDisplayName;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read toml from: " + jarPath + ", using file name as fallback.", e);
            return jarPath.getFileName().toString();
        }
        return jarPath.getFileName().toString();
    }


    private static boolean isServerOnlyMod(String modName) {
        if (modrinthCache.containsKey(modName)) {
            return modrinthCache.get(modName);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/search?query=" + URLEncoder.encode(modName, "UTF-8")))
                    .header("User-Agent", "Place-Boy/https://github.com/Place-Boy/MMMMM/1.0.1-beta")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray hits = json.getAsJsonArray("hits");

            if (hits != null && hits.size() > 0) {
                JsonObject mod = hits.get(0).getAsJsonObject();

                String clientSide = mod.has("client_side") ? mod.get("client_side").getAsString() : "required";
                String serverSide = mod.has("server_side") ? mod.get("server_side").getAsString() : "required";

                boolean result = "unsupported".equalsIgnoreCase(clientSide) && "required".equalsIgnoreCase(serverSide);
                modrinthCache.put(modName, result);
                return result;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query Modrinth for: " + modName, e);
        }

        modrinthCache.put(modName, false);
        return false;
    }
}
