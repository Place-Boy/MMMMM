package com.mmmmm;

import com.moandjiezana.toml.Toml;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.json.JSONArray;
import org.json.JSONObject;
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

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                Files.walk(modsFolder).forEach(path -> {
                    try {
                        if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                            String modName = getModNameFromJar(path);
                            if (modName != null && !isServerOnlyMod(modName, httpClient)) {
                                Path relativePath = modsFolder.relativize(path);
                                ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                zipOut.putNextEntry(zipEntry);
                                Files.copy(path, zipOut);
                                zipOut.closeEntry();
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
                executor.shutdown();
            }
        });
    }

    private static String getModNameFromJar(Path jarPath) {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/") && entry.getName().endsWith(".toml")) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Toml toml = new Toml().read(is);
                        return toml.getString("displayName"); // or "name" depending on the toml structure
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
                    .header("User-Agent", "Place-Boy/https://github.com/Place-Boy/MMMMM/1.0.1-beta") // Add your mod's name/version here
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            JSONArray hits = json.getJSONArray("hits");
            if (hits.length() > 0) {
                JSONObject mod = hits.getJSONObject(0);
                String side = mod.optString("side", "both");
                return "server".equalsIgnoreCase(side);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query Modrinth for: " + modName, e);
        }
        return false; // Default to including if unknown
    }

}
