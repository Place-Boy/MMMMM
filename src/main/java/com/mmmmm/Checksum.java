package com.mmmmm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.mmmmm.MMMMM.LOGGER;

/**
 * Utility class for computing file checksums and managing server synchronization.
 */
public class Checksum {

    /**
     * Computes the SHA-256 checksum of a file using a memory-safe buffer.
     *
     * @param filePath The path to the file.
     * @return The computed checksum as a hexadecimal string.
     * @throws Exception If an error occurs while reading the file or computing the checksum.
     */
    public static String computeChecksum(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(filePath);
             DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // read stream to update digest
            }
        }
        byte[] hashBytes = digest.digest();
        return HexFormat.of().formatHex(hashBytes);
    }

    public static void saveChecksums(Path zipFile, Path destination, Path checksumFile) throws Exception {
        Map<String, String> checksums = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String relativePath = entry.getName();
                    Path fileOnDisk = destination.resolve(relativePath).normalize();

                    // ONLY add to the JSON if it exists in the zip AND on disk
                    if (Files.exists(fileOnDisk)) {
                        checksums.put(relativePath, computeChecksum(fileOnDisk));
                    }
                }
            }
        }
        Files.writeString(checksumFile, new Gson().toJson(checksums));
    }

    public static void compareChecksums(Path zipFile, Path destination, Path checksumFile) throws Exception {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        // 1. Load the official "Source of Truth" from the previous checksum file
        Map<String, String> oldChecksums = Files.exists(checksumFile) ?
                gson.fromJson(Files.readString(checksumFile), mapType) : Collections.emptyMap();
        if (oldChecksums == null) oldChecksums = Collections.emptyMap();

        // 2. Identify exactly what the server wants us to have
        Set<String> zipEntries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) zipEntries.add(entry.getName());
            }
        }

        // 3. CLEANUP PHASE: Remove/Rename anything on disk that is NOT in the new ZIP
        // This is the critical part: If it's not in the server ZIP, it doesn't belong.
        for (String existingFile : oldChecksums.keySet()) {
            if (!zipEntries.contains(existingFile)) {
                Path fileToDelete = destination.resolve(existingFile).normalize();
                if (Files.exists(fileToDelete)) {
                    try {
                        Files.delete(fileToDelete);
                        LOGGER.info("Removed outdated file: " + existingFile);
                    } catch (Exception e) {
                        // Locked by game? Rename to .deleted so it's ignored on next load
                        Path renamed = fileToDelete.resolveSibling(fileToDelete.getFileName().toString() + ".deleted");
                        Files.move(fileToDelete, renamed, StandardCopyOption.REPLACE_EXISTING);
                        renamed.toFile().deleteOnExit();
                        LOGGER.info("Locked file renamed to .deleted: " + existingFile);
                    }
                }
            }
        }

        // 4. VERIFICATION PHASE: Only check files that exist in the server ZIP
        for (String mod : zipEntries) {
            Path file = destination.resolve(mod).normalize();
            if (Files.exists(file)) {
                String currentChecksum = computeChecksum(file);
                if (!oldChecksums.containsKey(mod)) {
                    LOGGER.info("New server file detected: " + mod);
                } else if (!currentChecksum.equals(oldChecksums.get(mod))) {
                    LOGGER.info("Modified server file: " + mod);
                }
            }
        }
    }
}