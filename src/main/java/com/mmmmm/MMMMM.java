package com.mmmmm;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

@Mod(MMMMM.MODID)
public class MMMMM {
    public static final String MODID = "mmmmm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MMMMM() {
        LOGGER.info("Initializing MMMMM...");

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "mmmmm-common.toml");
        LOGGER.info("Registered config spec hash: {}", Config.SPEC.hashCode());

        // Hook config loading and reloading events
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(Config::onLoad);
        modEventBus.addListener(Config::onReload);

        LOGGER.info("MMMMM initialized.");
    }
}
