package com.mmmmm;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config class to handle mod settings and updates.
 */
@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Configuration Options
    private static final ModConfigSpec.ConfigValue<Integer> BUTTON_X = BUILDER
            .comment("Button X position", "Default: -160")
            .define("buttonX", -160);

    private static final ModConfigSpec.ConfigValue<Integer> BUTTON_Y = BUILDER
            .comment("Button Y position", "Default: -235")
            .define("buttonY", -235);

    private static final ModConfigSpec.ConfigValue<String> PACK_URL = BUILDER
            .comment(
                    "URL to locate the modpack files or resource packs",
                    "This URL is used by the client to download and locate required assets."
            )
            .define("packUrl", "");

    private static final ModConfigSpec.ConfigValue<Integer> FILE_SERVER_PORT = BUILDER
            .comment(
                    "Port number for the file server to run on",
                    "Default: 8080"
            )
            .define("fileServerPort", 8080);

    private static final ModConfigSpec.ConfigValue<Boolean> ENABLE_FILE_SERVER = BUILDER
            .comment(
                    "Enable or disable the file hosting server.",
                    "Default value: true."
            )
            .define("enableFileServer", true);

    private static final ModConfigSpec.ConfigValue<String> FILE_DIRECTORY = BUILDER
            .comment(
                    "Server directory path to host shared files from.",
                    "Path should be specified relative to the server root.",
                    "Default: MMMMM/shared-files"
            )
            .define("fileDirectory", "MMMMM/shared-files");

    /**
     * Compile the final specification.
     */
    static final ModConfigSpec SPEC = BUILDER.build();

    // Runtime variables holding configuration
    public static int buttonX;
    public static int buttonY;
    public static String packURL;
    public static int fileServerPort;
    public static boolean enableFileServer;
    public static String fileDirectory;

    /**
     * Called when the configuration is loaded or updated. This ensures runtime
     * variables always hold accurate, current values.
     */
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Ensure the correct config type (COMMON) is loaded.
        if (!event.getConfig().getSpec().equals(SPEC)) {
            return;
        }

        // Update static values with configuration values
        buttonX = BUTTON_X.get();
        buttonY = BUTTON_Y.get();
        packURL = PACK_URL.get();
        fileServerPort = FILE_SERVER_PORT.get();
        enableFileServer = ENABLE_FILE_SERVER.get();
        fileDirectory = FILE_DIRECTORY.get();

        // Log configuration load
        MMMMM.LOGGER.info("Configuration loaded:");
        MMMMM.LOGGER.info("Button X: {}", buttonX);
        MMMMM.LOGGER.info("Button Y: {}", buttonY);
        MMMMM.LOGGER.info("Pack URL: {}", packURL);
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
        MMMMM.LOGGER.info("Enable File Server: {}", enableFileServer);
        MMMMM.LOGGER.info("File Directory: {}", fileDirectory);
    }
}