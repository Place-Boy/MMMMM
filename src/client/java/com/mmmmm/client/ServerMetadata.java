package com.mmmmm.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerMetadata {
    private static final File METADATA_FILE = new File("MMMMM/server_metadata.json");
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMetadata.class);
    private static Map<String, String> serverMetadata = new HashMap<>();

    static {
        loadMetadata();
    }

    private static void loadMetadata() {
        if (METADATA_FILE.exists()) {
            try (FileReader reader = new FileReader(METADATA_FILE)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loadedData = GSON.fromJson(reader, type);
                if (loadedData != null) {
                    serverMetadata = loadedData;
                }
                LOGGER.info("Metadata loaded successfully.");
            } catch (Exception e) {
                LOGGER.error("Failed to load metadata.", e);
            }
        } else {
            LOGGER.warn("Metadata file does not exist. Starting with an empty metadata map.");
        }
    }

    public static void saveMetadata() {
        try {
            File parentDir = METADATA_FILE.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                LOGGER.error("Failed to create metadata directory: {}", parentDir.getAbsolutePath());
                return;
            }

            try (FileWriter writer = new FileWriter(METADATA_FILE)) {
                GSON.toJson(serverMetadata, writer);
                LOGGER.info("Metadata saved successfully to {}", METADATA_FILE.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save metadata.", e);
        }
    }

    public static String getMetadata(String serverIP) {
        return serverMetadata.getOrDefault(serverIP, "");
    }

    public static void setMetadata(String serverIP, String value) {
        if (isValidServerIP(serverIP) && isValidMetadataValue(value)) {
            serverMetadata.put(serverIP, value);
            saveMetadata();
            LOGGER.info("Metadata updated for server: {}", serverIP);
        } else {
            LOGGER.warn("Invalid server IP or metadata value. Skipping update.");
        }
    }

    private static boolean isValidServerIP(String serverIP) {
        return serverIP != null && !serverIP.isBlank();
    }

    private static boolean isValidMetadataValue(String value) {
        return value != null && !value.isBlank();
    }

    public static Map<String, String> getAllMetadata() {
        return Collections.unmodifiableMap(serverMetadata);
    }
}