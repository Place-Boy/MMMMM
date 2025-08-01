package com.mmmmm;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static int fileServerPort = 8080;

    public static void registerConfig() {
        // Simple config loading (replace with Cloth Config for more features)
        Path configPath = Path.of("config", "mmmmm.properties");
        if (Files.exists(configPath)) {
            try {
                var props = new java.util.Properties();
                try (var in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
                fileServerPort = Integer.parseInt(props.getProperty("fileServerPort", "8080"));
            } catch (Exception e) {
                MMMMM.LOGGER.error("Failed to load config", e);
            }
        }
    }
}