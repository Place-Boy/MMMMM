package com.mmmmm;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.mmmmm.MMMMM.LOGGER;
import static net.minecraft.commands.Commands.literal;

public class RegisterCommands {
    private static java.nio.file.attribute.FileTime lastBuildTime = java.nio.file.attribute.FileTime.fromMillis(0);
    private static final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.Map<String, Boolean> modrinthCache = new java.util.HashMap<>();
    private static final HttpClient httpClient = HttpClient.newHttpClient();


    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mmmmm")
                    .then(literal("save-mods")
                            .requires(source -> source.getPermissionLevel() >= 2)
                            .executes(RegisterCommands::saveModsToZip)
                    )
            );
        });
    }

    public static int saveModsToZip(CommandContext<CommandSourceStack> context) {
        executor.execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");

            try {
                List<Path> modFiles = Files.walk(modsFolder)
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
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

                if (latestChange.compareTo(lastBuildTime) <= 0 && Files.exists(modsZip)) {
                    LOGGER.info("Mods have not changed since last build. Skipping zip creation.");
                    return;
                }

                LOGGER.info("Starting mods.zip creation. Found {} mods.", modFiles.size());

                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                    int total = modFiles.size();
                    int index = 0;

                    for (Path path : modFiles) {
                        index++;
                        try {
                            String modId = getModIdFromJar(path);
                            String modName = getModNameFromJar(path);
                            if (modId != null) {
                                boolean exclude = Config.filterServerSideMods && isServerOnlyMod(modId, modName);
                                if (!exclude) {
                                    Path relativePath = modsFolder.relativize(path);
                                    ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                    zipOut.putNextEntry(zipEntry);
                                    Files.copy(path, zipOut);
                                    zipOut.closeEntry();

                                    LOGGER.info("[{}/{}] Included mod: {} ({})",
                                            index, total, modName != null ? modName : modId, path.getFileName());
                                } else {
                                    LOGGER.info("[{}/{}] Excluded server-only mod: {} ({})",
                                            index, total, modName != null ? modName : modId, path.getFileName());
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
        context.getSource().sendSuccess(() -> Component.literal("Started mods.zip creation."), false);
        return 1;
    }

    /**
     * Reads fabric.mod.json from the JAR and returns the mod id.
     */
    private static String getModIdFromJar(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            Path modJson = fs.getPath("fabric.mod.json");
            if (Files.exists(modJson)) {
                String json = Files.readString(modJson);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("id")) {
                    return obj.get("id").getAsString();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read mod id from: " + jarPath, e);
        }
        return null;
    }

    /**
     * Reads fabric.mod.json from the JAR and returns the mod name.
     */
    private static String getModNameFromJar(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            Path modJson = fs.getPath("fabric.mod.json");
            if (Files.exists(modJson)) {
                String json = Files.readString(modJson);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("name")) {
                    LOGGER.info("Found mod name: {}", obj.get("name").getAsString());
                    return obj.get("name").getAsString();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read mod name from: " + jarPath, e);
        }
        return null;
    }

    /**
     * Checks if the mod is server-side only by querying Modrinth API.
     */
    private static boolean isServerOnlyMod(String modId, String modName) {
        if (modrinthCache.containsKey(modId)) {
            return modrinthCache.get(modId);
        }

        boolean result = false;
        try {
            // Try searching by mod id
            result = checkModrinthServerOnly(modId, modId, modName);
            // If not found, try searching by mod name
            if (!result && modName != null && !modName.isBlank()) {
                result = checkModrinthServerOnly(modName, modId, modName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query Modrinth for: " + modId, e);
        }
        modrinthCache.put(modId, result);
        return result;
    }

    private static boolean checkModrinthServerOnly(String query, String modId, String modName) throws Exception {
        String facets = URLEncoder.encode("[[\"project_type:mod\"]]", "UTF-8");
        String url = "https://api.modrinth.com/v2/search"
                + "?query=" + URLEncoder.encode(query, "UTF-8")
                + "&facets=" + facets
                + "&index=downloads"
                + "&limit=5";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Place-Boy/https://github.com/Place-Boy/MMMMM/1.0.1-beta")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray hits = json.getAsJsonArray("hits");

        if (hits != null && hits.size() > 0) {
            for (int i = 0; i < hits.size(); i++) {
                JsonObject mod = hits.get(i).getAsJsonObject();
                String slug = mod.has("slug") ? mod.get("slug").getAsString() : "";
                String projectId = mod.has("project_id") ? mod.get("project_id").getAsString() : "";
                String title = mod.has("title") ? mod.get("title").getAsString() : "";
                if (slug.equalsIgnoreCase(modId) || slug.equalsIgnoreCase(modName)
                        || projectId.equalsIgnoreCase(modId) || title.equalsIgnoreCase(modName)) {
                    String clientSide = mod.has("client_side") ? mod.get("client_side").getAsString() : "required";
                    String serverSide = mod.has("server_side") ? mod.get("server_side").getAsString() : "required";
                    return "unsupported".equalsIgnoreCase(clientSide) && "required".equalsIgnoreCase(serverSide);
                }
            }
        }
        return false;
    }
}
