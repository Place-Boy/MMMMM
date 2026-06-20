package com.mmmmm;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.moandjiezana.toml.Toml;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Use a single executor across runs
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static String cachedPublicIp = "Loading...";

    // Cache Modrinth lookups
    private static final Map<String, Boolean> modrinthCache = new HashMap<>();

    // Track last build time to avoid unnecessary zips
    private static FileTime lastBuildTime = FileTime.fromMillis(0);

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        fetchPublicIpAsync();
    }

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
                .then(Commands.literal("ip")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();

                            int port = Config.fileServerPort;
                            String fullAddress = cachedPublicIp + ":" + port;

                            var ipComponent = Component.literal(fullAddress)
                                    .withStyle(style -> style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fullAddress))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eClick to copy to clipboard")))
                                            .withColor(ChatFormatting.AQUA)
                                            .withUnderlined(true)
                                    );

                            var message = Component.literal("Server Address: ").append(ipComponent);

                            source.sendSuccess(() -> message, false);
                            return 1;
                        })
                )
                .then(Commands.literal("save-all")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveAllToZip();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Zipping mods... check console for progress."),
                                    true
                            );
                            return 1;
                        })
                )
                .then(Commands.literal("auto-filter")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            Config.filterServerSideMods = !Config.filterServerSideMods;
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Filter server-side mods: " + Config.filterServerSideMods),
                                    true
                            );
                            return 1;
                        })
                )
                .then(Commands.literal("filter-status")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Filter server-side mods: " + Config.filterServerSideMods),
                                    true
                            );
                            return 1;
                        })
                )
                .then(Commands.literal("filter")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("modName", StringArgumentType.greedyString())
                                        .executes(context -> {
                                                    String modName = StringArgumentType.getString(context, "modName");
                                                    addToFilter(modName);
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal("Added to filter: " + modName),
                                                            true
                                                    );
                                                    return 1;
                                                }
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("modName", StringArgumentType.greedyString())
                                        .executes(context -> {
                                                    String modName = StringArgumentType.getString(context, "modName");
                                                    removeFromFilter(modName);
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal("Removed from filter: " + modName),
                                                            true
                                                    );
                                                    return 1;
                                                }
                                        )
                                )
                        )
                )

        );
    }

    public static void fetchPublicIpAsync() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://checkip.amazonaws.com")) // Trusted text-only IP service
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            // .sendAsync pushes this request out of Minecraft's main loop entirely
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(ip -> {
                        // .trim() removes any sneaky hidden newlines (\n) returned by the web request
                        cachedPublicIp = ip.trim();
                    })
                    .exceptionally(ex -> {
                        // If the server has no internet or the API is down, fallback gracefully
                        cachedPublicIp = "127.0.0.1";
                        return null;
                    });
        } catch (Exception e) {
            cachedPublicIp = "127.0.0.1";
        }
    }

    public static void addToFilter(String modName) {
        Path filter = Path.of("MMMMM", "shared-files", "filter.json");
        List<String> filteredMods = new ArrayList<>();

        try {
            if (filter.getParent() != null) {
                Files.createDirectories(filter.getParent());
            }

            if (Files.exists(filter)) {
                try (Reader reader = Files.newBufferedReader(filter)) {
                    List<String> existing = GSON.fromJson(reader, new TypeToken<List<String>>() {
                    }.getType());
                    if (existing != null) {
                        filteredMods.addAll(existing);
                    }
                }
            }

            if (!filteredMods.contains(modName)) {
                filteredMods.add(modName);
            }

            try (Writer writer = Files.newBufferedWriter(filter)) {
                GSON.toJson(filteredMods, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void removeFromFilter(String modName) {
        Path filter = Path.of("MMMMM", "shared-files", "filter.json");
        List<String> filteredMods = new ArrayList<>();

        try {
            if (filter.getParent() != null) {
                Files.createDirectories(filter.getParent());
            }

            if (Files.exists(filter)) {
                try (Reader reader = Files.newBufferedReader(filter)) {
                    List<String> existing = GSON.fromJson(reader, new TypeToken<List<String>>() {
                    }.getType());
                    if (existing != null) {
                        filteredMods.addAll(existing);
                    }
                }
            }

            filteredMods.remove(modName);

            try (Writer writer = Files.newBufferedWriter(filter)) {
                GSON.toJson(filteredMods, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> loadFilterList() {
        Path filterPath = Path.of("MMMMM", "shared-files", "filter.json");
        if (!Files.exists(filterPath)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(filterPath)) {
            List<String> list = GSON.fromJson(reader, new TypeToken<List<String>>() {
            }.getType());
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            LOGGER.error("Failed to read filter.json", e);
            return new ArrayList<>();
        }
    }

    public static void saveAllToZip() {
        executor.execute(() -> {
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
        executor.execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");

            try {
                // Get list of .jar mods
                List<Path> modFiles = Files.walk(modsFolder)
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
                        .collect(Collectors.toList());

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

                    List<String> userFilteredMods = loadFilterList();

                    for (Path path : modFiles) {
                        index++;
                        try {
                            String modName = getModNameFromJar(path);
                            if (modName != null) {

                                // Check Modrinth API filter (if turned on)
                                boolean isServerOnly = Config.filterServerSideMods && isServerOnlyMod(modName);

                                // Check JSON filter (Matches exact name OR jar name like "jei-1.20.jar")
                                boolean isUserFiltered = userFilteredMods.contains(modName) || userFilteredMods.contains(path.getFileName().toString());

                                // Exclude if either filter catches it
                                boolean exclude = isServerOnly || isUserFiltered;

                                if (!exclude) {
                                    Path relativePath = modsFolder.relativize(path);
                                    ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                    zipOut.putNextEntry(zipEntry);
                                    Files.copy(path, zipOut);
                                    zipOut.closeEntry();

                                    LOGGER.info("[{}/{}] Included mod: {} ({})", index, total, modName, path.getFileName());
                                } else {
                                    LOGGER.info("[{}/{}] Excluded mod: {} ({}) [Reason: Custom Filter or Server-Side]", index, total, modName, path.getFileName());
                                }
                            } else {
                                LOGGER.warn("[{}/{}] Could not identify mod: {}", index, total, path.getFileName());
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
                    .uri(URI.create("https://api.modrinth.com/v2/search?query=" + URLEncoder.encode(modName, StandardCharsets.UTF_8)))
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
