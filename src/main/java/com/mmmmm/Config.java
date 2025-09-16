package com.mmmmm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    public static int fileServerPort = 8080;
    public static boolean filterServerSideMods = false;

    public static void registerConfig() {
        Path configPath = Path.of("config", "mmmmm.toml");
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                Properties defaults = new Properties();
                defaults.setProperty("fileServerPort", String.valueOf(fileServerPort));
                defaults.setProperty("filterServerSideMods", String.valueOf(filterServerSideMods));
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
                filterServerSideMods = Boolean.parseBoolean(props.getProperty("filterServerSideMods", "false"));

            }
        } catch (Exception e) {
            MMMMM.LOGGER.error("Failed to load or create config", e);
        }
    }
}