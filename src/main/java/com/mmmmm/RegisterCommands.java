package com.mmmmm;

<<<<<<< Updated upstream
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
=======
import com.moandjiezana.toml.Toml;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
>>>>>>> Stashed changes

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
<<<<<<< Updated upstream
=======
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
>>>>>>> Stashed changes
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static net.minecraft.server.command.CommandManager.literal;

public class RegisterCommands {
<<<<<<< Updated upstream
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

    private static int saveModsToZip(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal("Starting to save mods to zip in the background..."), false);

        new Thread(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");
            try {
                Files.createDirectories(modsZip.getParent());
                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                    Files.walk(modsFolder).forEach(path -> {
                        try {
                            if (Files.isRegularFile(path)) {
                                Path relativePath = modsFolder.relativize(path);
                                ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                zipOut.putNextEntry(zipEntry);
                                Files.copy(path, zipOut);
                                zipOut.closeEntry();
                            }
                        } catch (IOException e) {
                            MMMMM.LOGGER.error("Failed to add file to mods.zip: " + path, e);
                        }
                    });
                    MMMMM.LOGGER.info("Successfully created mods.zip in shared-files.");
                    context.getSource().sendFeedback(() -> Text.literal("Mods have been saved to mods.zip in the shared-files directory."), true);
                }
            } catch (IOException e) {
                MMMMM.LOGGER.error("Failed to create mods.zip", e);
                context.getSource().sendError(Text.literal("Failed to create mods.zip"));
            }
        }, "mmmmm-save-mods-zip").start();

        return 1;
=======

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCommands.class);

    @SubscribeEvent
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
                                if (!Config.filterServerMods || !isServerOnly) {
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
            ZipEntry entry = zipFile.getEntry("META-INF/mods.toml");
            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    Toml toml = new Toml().read(is);
                    // Try to get display_name at root (not standard for NeoForge, but fallback)
                    String rootDisplayName = toml.getString("display_name");
                    if (rootDisplayName != null) {
                        return rootDisplayName;
                    }
                    // Correct Forge: [[mods]] table
                    var modsList = toml.getTables("mods");
                    if (modsList != null && !modsList.isEmpty()) {
                        Toml firstMod = modsList.get(0);
                        String modDisplayName = firstMod.getString("displayName");
                        LOGGER.info("Found mod name: {}", modDisplayName);
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
            JSONObject json = new JSONObject(response.body());
            JSONArray hits = json.getJSONArray("hits");
            if (hits.length() > 0) {
                JSONObject mod = hits.getJSONObject(0);
                String clientSide = mod.optString("client_side", "required");
                String serverSide = mod.optString("server_side", "required");
                // Exclude if client_side is "unsupported" and server_side is "required"
                return "unsupported".equalsIgnoreCase(clientSide) && "required".equalsIgnoreCase(serverSide);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query Modrinth for: " + modName, e);
        }
        return false; // Default to including if unknown
>>>>>>> Stashed changes
    }
}