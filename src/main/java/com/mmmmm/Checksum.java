package com.mmmmm;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for computing file checksums.
 */
public class Checksum {

    private static final Logger LOGGER = LoggerFactory.getLogger(Checksum.class);

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

    public static void saveChecksums(Path modsDirectory, Path checksumFile) throws Exception {
        Map<String, String> checksums = Files.list(modsDirectory)
                .filter(Files::isRegularFile)
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString(),
                        path -> {
                            try {
                                return computeChecksum(path);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                ));

        Files.writeString(checksumFile, new Gson().toJson(checksums));
    }

    public static void compareChecksums(Path modsDirectory, Path checksumFile) throws Exception {
        // Load previous checksums
        Map<String, String> oldChecksums = new Gson().fromJson(Files.readString(checksumFile), Map.class);

        // Compute new checksums
        Map<String, String> newChecksums = Files.list(modsDirectory)
                .filter(Files::isRegularFile)
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString(),
                        path -> {
                            try {
                                return computeChecksum(path);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                ));

        // Compare checksums
        for (String mod : newChecksums.keySet()) {
            if (!oldChecksums.containsKey(mod)) {
                LOGGER.info("Checksum diff (since last snapshot) - Added: {}", mod);
            } else if (!newChecksums.get(mod).equals(oldChecksums.get(mod))) {
                LOGGER.info("Checksum diff (since last snapshot) - Modified: {}", mod);
            }
        }

        for (String mod : oldChecksums.keySet()) {
            if (!newChecksums.containsKey(mod)) {
                LOGGER.info("Checksum diff (since last snapshot) - Removed: {}", mod);
            }
        }
    }
}
