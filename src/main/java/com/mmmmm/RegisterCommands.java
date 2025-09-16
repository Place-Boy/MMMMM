package com.mmmmm;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

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
import static net.minecraft.server.command.CommandManager.literal;

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
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(RegisterCommands::saveModsToZip)
                    )
            );
        });
    }

    public static int saveModsToZip(CommandContext<ServerCommandSource> context) {
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
                            String modName = getModNameFromJar(path); // Implement for Fabric
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
                                    LOGGER.info("[{}/{}] Excluded server-only mod: {} ({})",
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
        context.getSource().sendFeedback(() -> Text.literal("Started mods.zip creation."), false);
        return 1;
    }


    /**
     * Reads fabric.mod.json from the JAR and returns the mod id or name.
     */
    private static String getModNameFromJar(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            Path modJson = fs.getPath("fabric.mod.json");
            if (Files.exists(modJson)) {
                String json = Files.readString(modJson);
                // Use a JSON library for production; here is a simple parse:
                int idIndex = json.indexOf("\"id\"");
                if (idIndex != -1) {
                    int colon = json.indexOf(':', idIndex);
                    int quote1 = json.indexOf('"', colon);
                    int quote2 = json.indexOf('"', quote1 + 1);
                    return json.substring(quote1 + 1, quote2);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Checks if the mod is server-side only by querying Modrinth API.
     */
    private static boolean isServerOnlyMod(String modName) {
        if (modrinthCache.containsKey(modName)) {
            return modrinthCache.get(modName);
        }

        try {
            String url = "https://api.modrinth.com/v2/search?query=" + URLEncoder.encode(modName, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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