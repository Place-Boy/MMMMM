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

    /**
     * Compile the final specification.
     */
    static final ModConfigSpec SPEC = BUILDER.build();

    public static int fileServerPort;

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

        // Log configuration load
        MMMMM.LOGGER.info("Configuration loaded:");
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
    }
}