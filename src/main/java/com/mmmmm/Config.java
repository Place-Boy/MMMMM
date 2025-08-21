package com.mmmmm;

import com.mmmmm.MMMMM;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    public static int fileServerPort = 8080;

<<<<<<< Updated upstream
    public static void registerConfig() {
        Path configPath = Path.of("config", "mmmmm");
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                Properties defaults = new Properties();
                defaults.setProperty("fileServerPort", String.valueOf(fileServerPort));
                try (var out = Files.newOutputStream(configPath)) {
                    defaults.store(out, "MMMMM Mod Configuration");
                }
            }
            if (Files.exists(configPath)) {
                var props = new Properties();
                try (var in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
                fileServerPort = Integer.parseInt(props.getProperty("fileServerPort", "8080"));
            }
        } catch (Exception e) {
            MMMMM.LOGGER.error("Failed to load or create config", e);
        }
    }
}
=======
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue FILE_SERVER_PORT = BUILDER
            .comment("Port number for the file server to run on", "Default: 8080")
            .defineInRange("fileServerPort", 8080, 1, 65535);
    public static final ForgeConfigSpec.BooleanValue FILTER_SERVER_MODS = BUILDER
            .comment("If true, server-only mods will be excluded from the zip.",
                    "This trys to decrease download time for clients.",
                    "Disable this if server is crashing on client join due to missing mods.",
                    "Default: false")
            .define("filterServerMods", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int fileServerPort;
    public static boolean filterServerMods;

    static {
        MMMMM.LOGGER.info("Config class static block executed.");
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        MMMMM.LOGGER.info("onLoad fired for mod ID: {}", event.getConfig().getModId());
        MMMMM.LOGGER.info("Received config spec hash: {}", event.getConfig().getSpec().hashCode());
        if (event.getConfig().getSpec() != SPEC) {
            MMMMM.LOGGER.info("Config load skipped - not our spec.");
            return;
        }
        fileServerPort = FILE_SERVER_PORT.get();
        filterServerMods = FILTER_SERVER_MODS.get();

        MMMMM.LOGGER.info("Configuration loaded:");
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
        MMMMM.LOGGER.info("Filter Server Mods: {}", filterServerMods);
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        MMMMM.LOGGER.info("onReload fired for mod ID: {}", event.getConfig().getModId());
        MMMMM.LOGGER.info("Received config spec hash: {}", event.getConfig().getSpec().hashCode());
        if (event.getConfig().getSpec() != SPEC) {
            MMMMM.LOGGER.info("Config reload skipped - not our spec.");
            return;
        }
        fileServerPort = FILE_SERVER_PORT.get();
        filterServerMods = FILTER_SERVER_MODS.get();
        MMMMM.LOGGER.info("Configuration reloaded:");
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
        MMMMM.LOGGER.info("Filter Server Mods: {}", filterServerMods);
    }
}
>>>>>>> Stashed changes
