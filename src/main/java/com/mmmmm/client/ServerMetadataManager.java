package com.mmmmm.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ServerMetadataManager {
    private static final File METADATA_FILE = new File("MMMMM/server_metadata.json");
    private static final Gson GSON = new Gson();
    private static Map<String, String> serverDownloadIPs = new HashMap<>();

    static {
        loadMetadata();
    }

    private static void loadMetadata() {
        if (METADATA_FILE.exists()) {
            try (FileReader reader = new FileReader(METADATA_FILE)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                serverDownloadIPs = GSON.fromJson(reader, type);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveMetadata() {
        try (FileWriter writer = new FileWriter(METADATA_FILE)) {
            GSON.toJson(serverDownloadIPs, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getDownloadIP(String serverIP) {
        return serverDownloadIPs.getOrDefault(serverIP, "");
    }

    public static void setDownloadIP(String serverIP, String downloadIP) {
        serverDownloadIPs.put(serverIP, downloadIP);
        saveMetadata();
    }
}
