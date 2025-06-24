package com.mmmmm;

import com.mmmmm.server.FileHostingServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.io.IOException;

/**
 * Config class to handle mod settings and updates.
 */
@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<Integer> FILE_SERVER_PORT = BUILDER
            .comment("Port number for the file server to run on. Default: 8080")
            .define("fileServerPort", 8080);

    /**
     * Compile the final specification.
     */
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int fileServerPort;

    /**
     * Called when the configuration is loaded or updated. This ensures runtime
     * variables always hold accurate, current values.
     */
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (!event.getConfig().getSpec().equals(Config.SPEC)) return;

        Config.fileServerPort = Config.FILE_SERVER_PORT.get();
        MMMMM.LOGGER.info("Config loaded - fileServerPort = {}", Config.fileServerPort);

        try {
            FileHostingServer.start();
            MMMMM.LOGGER.info("Called FileHostingServer.start()");
        } catch (IOException e) {
            MMMMM.LOGGER.error("Failed to start file hosting server: ", e);
        }
    }
}
