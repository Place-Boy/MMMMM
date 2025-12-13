package com.mmmmm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.reflect.Type;

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

    public static void saveChecksums(Path modsDirectory, Path checksumFile) throws Exception {
        try (Stream<Path> stream = Files.list(modsDirectory)) {
            Map<String, String> checksums = stream
                    .filter(Files::isRegularFile)
                    .sorted() // stable order
                    .collect(Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> {
                                try {
                                    return computeChecksum(path);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }, (a, b) -> a, LinkedHashMap::new
                    ));

            Files.createDirectories(checksumFile.getParent());
            Files.writeString(checksumFile, new Gson().toJson(checksums));
        }
    }

    public static void compareChecksums(Path modsDirectory, Path checksumFile) throws Exception {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        Map<String, String> oldChecksums;
        if (!Files.exists(checksumFile)) {
            System.out.println("Checksum file does not exist: " + checksumFile + " — treating all files as added.");
            oldChecksums = Map.of();
        } else {
            String json = Files.readString(checksumFile);
            oldChecksums = gson.fromJson(json, mapType);
            if (oldChecksums == null) oldChecksums = Map.of();
        }

        Map<String, String> newChecksums;
        try (Stream<Path> stream = Files.list(modsDirectory)) {
            newChecksums = stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> {
                                try {
                                    return computeChecksum(path);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }, (a, b) -> a, LinkedHashMap::new
                    ));
        }

        boolean any = false;
        for (String mod : newChecksums.keySet()) {
            if (!oldChecksums.containsKey(mod)) {
                System.out.println("Added: " + mod);
                any = true;
            } else if (!newChecksums.get(mod).equals(oldChecksums.get(mod))) {
                System.out.println("Modified: " + mod);
                any = true;
            }
        }

        for (String mod : oldChecksums.keySet()) {
            if (!newChecksums.containsKey(mod)) {
                System.out.println("Removed: " + mod);
                any = true;
            }
        }

        if (!any) {
            System.out.println("No changes detected.");
        }
    }

    /**
     * Small CLI to compute/save/compare checksums.
     * Usage:
     *   compute <file>
     *   save <modsDir> <checksumFile>
     *   compare <modsDir> <checksumFile>
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Checksum compute|save|compare ...");
            System.exit(2);
        }

        try {
            String cmd = args[0];
            switch (cmd) {
                case "compute": {
                    if (args.length != 2) throw new IllegalArgumentException("compute requires <file>");
                    Path file = Path.of(args[1]);
                    System.out.println(computeChecksum(file));
                    break;
                }
                case "save": {
                    if (args.length != 3) throw new IllegalArgumentException("save requires <modsDir> <checksumFile>");
                    Path dir = Path.of(args[1]);
                    Path out = Path.of(args[2]);
                    saveChecksums(dir, out);
                    System.out.println("Saved checksums to " + out);
                    break;
                }
                case "compare": {
                    if (args.length != 3) throw new IllegalArgumentException("compare requires <modsDir> <checksumFile>");
                    Path dir = Path.of(args[1]);
                    Path in = Path.of(args[2]);
                    compareChecksums(dir, in);
                    break;
                }
                default:
                    System.err.println("Unknown command: " + cmd);
                    System.exit(2);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}