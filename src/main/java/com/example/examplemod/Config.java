package com.example.examplemod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// Config class to handle mod settings
@EventBusSubscriber(modid = ExampleMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Configuration Options

    private static final ModConfigSpec.ConfigValue<Integer> BUTTON_X = BUILDER
            .comment("Button X position")
            .define("buttonX", 0);

    private static final ModConfigSpec.ConfigValue<Integer> BUTTON_Y = BUILDER
            .comment("Button Y position")
            .define("buttonY", 0);

    // URL for modpack downloads or resource file hosting
    private static final ModConfigSpec.ConfigValue<String> PACK_URL = BUILDER
            .comment("URL to locate the modpack files or resource packs for the client to download")
            .define("packUrl", "http://localhost:8080");

    // Port for the file server
    private static final ModConfigSpec.ConfigValue<Integer> FILE_SERVER_PORT = BUILDER
            .comment("Port number for the file server to run on")
            .define("fileServerPort", 8080);

    // Enable or disable the file-hosting server
    private static final ModConfigSpec.ConfigValue<Boolean> ENABLE_FILE_SERVER = BUILDER
            .comment("Enable or disable the file hosting server")
            .define("enableFileServer", true);

    // Path to the folder to host files from
    private static final ModConfigSpec.ConfigValue<String> FILE_DIRECTORY = BUILDER
            .comment("Path in the server directory to host files from")
            .define("fileDirectory", "shared-files");

    // More mod-specific options can be added here...

    static final ModConfigSpec SPEC = BUILDER.build();

    // Static values to hold configuration settings
    public static int buttonX;
    public static int buttonY;
    public static String packURL; // Stores the value of 'packUrl'
    public static int fileServerPort; // Stores the value of 'fileServerPort'
    public static boolean enableFileServer; // Stores if the file server is enabled
    public static String fileDirectory; // Stores the file directory

    // Called when the mod configuration is loaded or updated
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        buttonX = BUTTON_X.get();
        buttonY = BUTTON_Y.get();
        packURL = PACK_URL.get();
        fileServerPort = FILE_SERVER_PORT.get();
        enableFileServer = ENABLE_FILE_SERVER.get();
        fileDirectory = FILE_DIRECTORY.get();
    }
}