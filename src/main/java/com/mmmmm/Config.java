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

    private static final ModConfigSpec.ConfigValue<Integer> FILE_SERVER_PORT = BUILDER
            .comment(
                    "Port number for the file server to run on",
                    "Default: 8080"
            )
            .define("fileServerPort", 8080);

    private static final ModConfigSpec.ConfigValue<Boolean> FILTER_SERVER_MODS = BUILDER
            .comment(
                    "If true, server-only mods will be excluded from the zip.",
                    "This trys to decrease download time for clients.",
                    "Disable this if server is crashing on client join due to missing mods.",
                    "Default: false"
            )
            .define("filterServerMods", false);

    /**
     * Compile the final specification.
     */
    static final ModConfigSpec SPEC = BUILDER.build();

    public static int fileServerPort;
    public static boolean filterServerSideMods;

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
        fileServerPort = FILE_SERVER_PORT.get();
        filterServerSideMods = FILTER_SERVER_MODS.get();

        // Log configuration load
        MMMMM.LOGGER.info("Configuration loaded:");
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
        MMMMM.LOGGER.info("Filter Server Mods: {}", filterServerSideMods);
    }
}