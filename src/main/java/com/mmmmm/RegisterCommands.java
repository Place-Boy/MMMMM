package com.mmmmm;

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
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RegisterCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCommands.class);

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering server commands...");

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mmmmm")
                .then(Commands.literal("save-mods")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveModsToZip();
                            context.getSource().sendSuccess(() -> Component.literal("Mods have been saved to mods.zip in the shared-files directory."), true);
                            return 1;
                        })
                )
        );
    }

    public static void saveModsToZip() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");
            HttpClient httpClient = HttpClient.newHttpClient();

            var includedMods = new java.util.ArrayList<String>();
            var excludedMods = new java.util.ArrayList<String>();

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                Files.walk(modsFolder).forEach(path -> {
                    try {
                        if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                            String modName = getModNameFromJar(path);
                            if (modName != null) {
                                boolean isServerOnly = isServerOnlyMod(modName, httpClient);
                                if (!Config.filterServerSideMods || !isServerOnly) {
                                    Path relativePath = modsFolder.relativize(path);
                                    ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                    zipOut.putNextEntry(zipEntry);
                                    Files.copy(path, zipOut);
                                    zipOut.closeEntry();
                                    includedMods.add(modName + " (" + path.getFileName() + ")");
                                } else {
                                    excludedMods.add(modName + " (" + path.getFileName() + ")");
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to process mod: " + path, e);
                    }
                });
                LOGGER.info("Successfully created mods.zip in shared-files.");
            } catch (IOException e) {
                LOGGER.error("Failed to create mods.zip", e);
            } finally {
                LOGGER.info("Included mods: {}", includedMods.isEmpty() ? "None" : String.join(", ", includedMods));
                LOGGER.info("Excluded mods: {}", excludedMods.isEmpty() ? "None" : String.join(", ", excludedMods));
                executor.shutdown();
            }
        });
    }

    private static String getModNameFromJar(Path jarPath) {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/neoforge.mods.toml");
            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    Toml toml = new Toml().read(is);
                    // Try to get display_name at root (not standard for NeoForge, but fallback)
                    String rootDisplayName = toml.getString("display_name");
                    if (rootDisplayName != null) {
                        return rootDisplayName;
                    }
                    // Correct NeoForge: [[mods]] table
                    var modsList = toml.getTables("mods");
                    if (modsList != null && !modsList.isEmpty()) {
                        Toml firstMod = modsList.get(0);
                        String modDisplayName = firstMod.getString("displayName");
                        if (modDisplayName != null) {
                            LOGGER.info("Found mod name: " + modDisplayName);
                            return modDisplayName;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read toml from: " + jarPath, e);
        }
        return null;
    }

    private static boolean isServerOnlyMod(String modName, HttpClient httpClient) {
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

                // Exclude if client_side is "unsupported" and server_side is "required"
                return "unsupported".equalsIgnoreCase(clientSide) && "required".equalsIgnoreCase(serverSide);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query Modrinth for: " + modName, e);
        }
        return false; // Default to including if unknown
    }


}
