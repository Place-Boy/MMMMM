package com.mmmmm;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mod.EventBusSubscriber(modid = MMMMM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RegisterCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCommands.class);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering server commands...");

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mmmmm")
                .then(Commands.literal("save-mods")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveModsToZip();
                            context.getSource().sendSuccess(() -> Component.literal("Mods have been saved to mods.zip in the shared-files directory."), true);
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("mmmmm")
                .then(Commands.literal("save-all")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            saveAllToZip();
                            context.getSource().sendSuccess(() -> Component.literal("Mods, config and kubejs have been saved to separate zip files in the shared-files directory"), true);
                            return 1;
                        })
                )
        );
    }

    public static void saveAllToZip(){
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            saveModsToZip();
            saveFolderToZip(Path.of("config"), Path.of("MMMMM", "shared-files", "config.zip"));
            saveFolderToZip(Path.of("kubejs"), Path.of("MMMMM", "shared-files", "kubejs.zip"));
        });
        exec.shutdown();
    }

    private static void saveFolderToZip(Path sourceFolder, Path targetZip) {
        try {
            Path parent = targetZip.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(targetZip))) {
                addFolderToZip(sourceFolder, sourceFolder, zipOut);
                LOGGER.info("Successfully created " + targetZip.getFileName() + " in shared-files.");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create " + targetZip.getFileName(), e);
        }
    }

    public static void addFolderToZip(Path rootFolder, Path sourceFolder, ZipOutputStream zipOut) throws IOException {
        if (!Files.exists(sourceFolder)) {
            LOGGER.info("Source folder does not exist, skipping: " + sourceFolder);
            return;
        }

        Files.walk(sourceFolder)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Path relativePath = rootFolder.relativize(path);
                        // Ensure zip entries use '/' as separator
                        String zipEntryName = relativePath.toString().replace('\\', '/');
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
                        zipOut.putNextEntry(zipEntry);
                        Files.copy(path, zipOut);
                        zipOut.closeEntry();
                    } catch (IOException e) {
                        LOGGER.error("Failed to add file to zip: " + path, e);
                    }
                });
    }

    public static void saveModsToZip() {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> saveFolderToZip(Path.of("mods"), Path.of("MMMMM", "shared-files", "mods.zip")));
        exec.shutdown();
    }
}
