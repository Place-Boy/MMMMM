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
                            context.getSource().sendSuccess(() -> Component.literal("Mods, config and kubejs have been saved to mmmmm.zop in the shared-files directory"), true);
                            return 1;
                        })
                )
        );
    }

    public static void saveAllToZip(){
        Executors.newSingleThreadExecutor().execute(() -> {
            Path modsFolder = Path.of("mods");
            Path configFolder = Path.of("config");
            Path kubejsFolder = Path.of("kubejs");
            Path allZip = Path.of("MMMMM/shared-files/mmmmm.zip");

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(allZip))) {
                // Helper method to add folder contents to zip
                addFolderToZip(modsFolder, modsFolder, zipOut);
                addFolderToZip(configFolder, configFolder, zipOut);
                addFolderToZip(kubejsFolder, kubejsFolder, zipOut);

                LOGGER.info("Successfully created mmmmm.zip in shared-files.");
            } catch (IOException e) {
                LOGGER.error("Failed to create mmmmm.zip", e);
            }
            finally {
                Executors.newSingleThreadExecutor().shutdown();
            }
        });
    }

    public static void addFolderToZip(Path rootFolder, Path sourceFolder, ZipOutputStream zipOut) throws IOException {
        Files.walk(sourceFolder).forEach(path -> {
            try {
                if (Files.isRegularFile(path)) {
                    Path relativePath = rootFolder.relativize(path);
                    ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                    zipOut.putNextEntry(zipEntry);
                    Files.copy(path, zipOut);
                    zipOut.closeEntry();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to add file to zip: " + path, e);
            }
        });
    }

    public static void saveModsToZip() {
        Executors.newSingleThreadExecutor().execute(() -> {
            Path modsFolder = Path.of("mods");
            Path modsZip = Path.of("MMMMM/shared-files/mods.zip");

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(modsZip))) {
                addFolderToZip(modsFolder, modsFolder, zipOut);
                LOGGER.info("Successfully created mods.zip in shared-files.");
            } catch (IOException e) {
                LOGGER.error("Failed to create mods.zip", e);
            } finally {
                Executors.newSingleThreadExecutor().shutdown();
            }
        });
    }
}