package com.mmmmm;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.mmmmm.MMMMM.LOGGER;

/**
 * Utility class for computing file checksums.
 */
public class Checksum {

    /**
     * Computes the SHA-256 checksum of a file.
     *
     * @param filePath The path to the file.
     * @return The computed checksum as a hexadecimal string.
     * @throws Exception If an error occurs while reading the file or computing the checksum.
     */
    public static String computeChecksum(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(filePath);
        byte[] hashBytes = digest.digest(fileBytes);
        return HexFormat.of().formatHex(hashBytes);
    }

    public static void saveChecksums(Path zipFile, Path destination, Path checksumFile) throws Exception {
        Map<String, String> checksums = new java.util.HashMap<>();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String relativePath = entry.getName();
                    Path extractedFile = destination.resolve(relativePath).normalize();
                    if (Files.isRegularFile(extractedFile)) {
                        checksums.put(relativePath, computeChecksum(extractedFile));
                        LOGGER.info("Computed checksum for " + relativePath + ": " + checksums.get(relativePath));
                    }
                }
            }
        }

        Files.writeString(checksumFile, new Gson().toJson(checksums));
    }

    public static void compareChecksums(Path zipFile, Path destination, Path checksumFile) throws Exception {
        // Load previous checksums
        Map<String, String> oldChecksums = new Gson().fromJson(Files.readString(checksumFile), Map.class);
        if (oldChecksums == null) {
            oldChecksums = java.util.Collections.emptyMap();
        }

        // Get files inside the new zip
        java.util.Set<String> zipEntries = new java.util.HashSet<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    zipEntries.add(entry.getName());
                }
            }
        }

        for (String mod : zipEntries) {
            Path file = destination.resolve(mod).normalize();
            if (!oldChecksums.containsKey(mod)) {
                LOGGER.info("Added: " + mod);
            } else if (Files.isRegularFile(file)) {
                String currentChecksum = computeChecksum(file);
                if (!currentChecksum.equals(oldChecksums.get(mod))) {
                    LOGGER.info("Modified: " + mod);
                }
            }
        }

        for (String mod : oldChecksums.keySet()) {
            if (!zipEntries.contains(mod)) {
                Path fileToDelete = destination.resolve(mod).normalize();
                try {
                    Files.deleteIfExists(fileToDelete);
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete " + mod + " (locked by game). Queuing for alternative removal.");

                    fileToDelete.toFile().deleteOnExit();

                    try {
                        Path renamedFile = fileToDelete.resolveSibling(fileToDelete.getFileName().toString() + ".deleted");
                        Files.move(fileToDelete, renamedFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        renamedFile.toFile().deleteOnExit();
                        LOGGER.info("Renamed locked file to: " + renamedFile.getFileName());
                    } catch (Exception renameException) {
                        LOGGER.error("Could not rename the locked file. It will be deleted when the game closes.", renameException);
                    }
                }
            }
        }
    }
}