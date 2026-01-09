package com.mmmmm;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class Config {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue FILE_SERVER_PORT = BUILDER
            .comment("Port number for the file server to run on", "Default: 8080")
            .defineInRange("fileServerPort", 8080, 1, 65535);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int fileServerPort;

    static {
        MMMMM.LOGGER.info("Config class static block executed.");
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        MMMMM.LOGGER.info("onLoad fired for mod ID: {}", event.getConfig().getModId());
        MMMMM.LOGGER.info("Received config spec hash: {}", event.getConfig().getSpec().hashCode());
        if (event.getConfig().getSpec() != SPEC) {
            MMMMM.LOGGER.info("Config load skipped - not our spec.");
            return;
        }
        fileServerPort = FILE_SERVER_PORT.get();
        MMMMM.LOGGER.info("Configuration loaded:");
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        MMMMM.LOGGER.info("onReload fired for mod ID: {}", event.getConfig().getModId());
        MMMMM.LOGGER.info("Received config spec hash: {}", event.getConfig().getSpec().hashCode());
        if (event.getConfig().getSpec() != SPEC) {
            MMMMM.LOGGER.info("Config reload skipped - not our spec.");
            return;
        }
        fileServerPort = FILE_SERVER_PORT.get();
        MMMMM.LOGGER.info("Configuration reloaded:");
        MMMMM.LOGGER.info("File Server Port: {}", fileServerPort);
    }
}
