package com.mmmmm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.minecraft.server.command.CommandManager.literal;

public class RegisterCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mmmmm")
                    .then(literal("save-mods")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(RegisterCommands::saveModsToZip)
                    )
            );
        });
    }

    private static int saveModsToZip(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal("Starting to save mods to zip in the background..."), false);

        new Thread(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");
            try {
                Files.createDirectories(modsZip.getParent());
                try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                    Files.walk(modsFolder).forEach(path -> {
                        try {
                            if (Files.isRegularFile(path)) {
                                Path relativePath = modsFolder.relativize(path);
                                ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                                zipOut.putNextEntry(zipEntry);
                                Files.copy(path, zipOut);
                                zipOut.closeEntry();
                            }
                        } catch (IOException e) {
                            MMMMM.LOGGER.error("Failed to add file to mods.zip: " + path, e);
                        }
                    });
                    MMMMM.LOGGER.info("Successfully created mods.zip in shared-files.");
                    context.getSource().sendFeedback(() -> Text.literal("Mods have been saved to mods.zip in the shared-files directory."), true);
                }
            } catch (IOException e) {
                MMMMM.LOGGER.error("Failed to create mods.zip", e);
                context.getSource().sendError(Text.literal("Failed to create mods.zip"));
            }
        }, "mmmmm-save-mods-zip").start();

        return 1;
    }
}