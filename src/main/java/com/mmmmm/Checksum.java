package com.mmmmm;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

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
                System.out.println("Added: " + mod);
            } else if (Files.isRegularFile(file)) {
                String currentChecksum = computeChecksum(file);
                if (!currentChecksum.equals(oldChecksums.get(mod))) {
                    System.out.println("Modified: " + mod);
                }
            }
        }

        for (String mod : oldChecksums.keySet()) {
            if (!zipEntries.contains(mod)) {
                System.out.println("Removed: " + mod);
                try {
                    Files.deleteIfExists(destination.resolve(mod).normalize());
                } catch (Exception e) {
                    System.err.println("Failed to delete " + mod + " (might be locked by the game): " + e.getMessage());
                }
            }
        }
    }
}