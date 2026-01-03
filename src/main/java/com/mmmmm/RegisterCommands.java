package com.mmmmm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Mod.EventBusSubscriber(modid = MMMMM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RegisterCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCommands.class);

    // Use a single executor across runs
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Cache Modrinth lookups
    private static final Map<String, Boolean> modrinthCache = new HashMap<>();

    // Track last build time to avoid unnecessary zips
    private static FileTime lastBuildTime = FileTime.fromMillis(0);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering server commands...");

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mmmmm")
                .then(Commands.literal("save-mods")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveModsToZip();
                            context.getSource().sendSuccess(() -> Component.literal("Zipping mods... check console for progress."), true);
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
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            saveFolderToZip(Path.of("mods"), Path.of("MMMMM", "shared-files", "mods.zip"));
            saveFolderToZip(Path.of("config"), Path.of("MMMMM", "shared-files", "config.zip"));
            saveFolderToZip(Path.of("kubejs"), Path.of("MMMMM", "shared-files", "kubejs.zip"));
        });
        exec.shutdown();
    }

    private static void saveFolderToZip(Path sourceFolder, Path targetZip) {
        try {
            Path parent = targetZip.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(targetZip))) {
                addFolderToZip(sourceFolder, sourceFolder, zipOut);
                LOGGER.info("Successfully created " + targetZip.getFileName() + " in shared-files.");
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

        try (Stream<Path> stream = Files.walk(sourceFolder)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Path relativePath = rootFolder.relativize(path);
                            // Ensure zip entries use '/' as separator
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
    }

    public static void saveModsToZip() {
        executor.execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM", "shared-files", "mods.zip");

            try {
                // Get list of .jar mods
                List<Path> modFiles;
                try (Stream<Path> stream = Files.walk(modsFolder)) {
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

                Path parent = modsZip.getParent();
                if (parent != null) Files.createDirectories(parent);

                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                    int total = modFiles.size();
                    int index = 0;

                    for (Path path : modFiles) {
                        index++;
                        try {
                            String modName = getModNameFromJar(path);
                            boolean exclude = false;
                            try {
                                exclude = Config.filterServerSideMods && isServerOnlyMod(modName);
                            } catch (Exception e) {
                                LOGGER.warn("Modrinth check failed for {}: {}", modName, e.getMessage());
                            }

                            if (!exclude) {
                                Path relativePath = modsFolder.relativize(path);
                                ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                                zipOut.putNextEntry(zipEntry);
                                Files.copy(path, zipOut);
                                zipOut.closeEntry();

                                LOGGER.info("[{}/{}] Included mod: {} ({})",
                                        index, total, modName, path.getFileName());
                            } else {
                                LOGGER.info("[{}/{}] Excluded mod: {} ({})",
                                        index, total, modName, path.getFileName());
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

    private static String getModNameFromJar(Path jarPath) {
        // Read META-INF/neoforge.mods.toml if present and extract display_name or mods[0].displayName
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/neoforge.mods.toml");
            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    String toml = sb.toString();

                    String name = extractDisplayNameFromToml(toml);
                    if (name != null && !name.isBlank()) return name;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read toml from: " + jarPath + ", using file name as fallback.", e);
            return jarPath.getFileName().toString();
        }
        return jarPath.getFileName().toString();
    }

    private static String extractDisplayNameFromToml(String toml) {
        // Try root display_name = "..."
        Pattern p1 = Pattern.compile("(?m)^\\s*display_name\\s*=\\s*\"([^\"]+)\"");
        Matcher m1 = p1.matcher(toml);
        if (m1.find()) return m1.group(1).trim();

        // Try mods table first entry displayName
        // Look for something like [[mods]] then find displayName = "..." after it
        Pattern modsTable = Pattern.compile("(?m)^\\s*\\[\\[mods\\]\\]", Pattern.MULTILINE);
        Matcher mTable = modsTable.matcher(toml);
        if (mTable.find()) {
            int start = mTable.end();
            String after = toml.substring(start);
            Pattern p2 = Pattern.compile("(?m)^\\s*displayName\\s*=\\s*\"([^\"]+)\"");
            Matcher m2 = p2.matcher(after);
            if (m2.find()) return m2.group(1).trim();
        }

        return null;
    }

    private static boolean isServerOnlyMod(String modName) {
        if (modrinthCache.containsKey(modName)) {
            return modrinthCache.get(modName);
        }

        try {
            String encoded = URLEncoder.encode(modName, StandardCharsets.UTF_8.name());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/search?query=" + encoded))
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
