package com.mmmmm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;

public class Checksum {

    private static final Logger LOGGER = LoggerFactory.getLogger(Checksum.class);

    public static void compareChecksums(Path zipFilePath, Path destination, Path checksumFile) throws Exception {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        // Ensure we load a mutable map
        Map<String, String> oldChecksums = new HashMap<>();
        if (Files.exists(checksumFile)) {
            Map<String, String> loaded = gson.fromJson(Files.readString(checksumFile), mapType);
            if (loaded != null) {
                oldChecksums.putAll(loaded);
            }
        }

        Set<String> zipEntries = new HashSet<>();

        // Open the archive as a ZipFile for rapid header access
        try (ZipFile zip = new ZipFile(zipFilePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                zipEntries.add(entryName);

                Path targetFile = destination.resolve(entryName).normalize();

                // Convert the embedded CRC to a standard hex string
                String serverCrc = Long.toHexString(entry.getCrc());

                // Robust check: missing on disk, missing in json, OR content hashes don't match
                boolean needsUpdate = !Files.exists(targetFile)
                        || !oldChecksums.containsKey(entryName)
                        || !oldChecksums.get(entryName).equals(serverCrc);

                if (needsUpdate) {
                    LOGGER.info("Updating/Extracting file: {}", entryName);

                    if (targetFile.getParent() != null) {
                        Files.createDirectories(targetFile.getParent());
                    }

                    // Direct target stream copy
                    try (InputStream is = zip.getInputStream(entry)) {
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    oldChecksums.put(entryName, serverCrc);
                }
            }
        }

        // Handle removals
        for (String oldFile : new HashSet<>(oldChecksums.keySet())) {
            if (!zipEntries.contains(oldFile)) {
                LOGGER.info("File {} removed from server package. Deleting locally.", oldFile);
                Files.deleteIfExists(destination.resolve(oldFile).normalize());
                oldChecksums.remove(oldFile);
            }
        }

        // Save progress manifest
        Files.writeString(checksumFile, gson.toJson(oldChecksums));
    }
}