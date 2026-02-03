package com.mmmmm.core;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Main mod class for ExampleMod.
 */
@Mod(MMMMM.MODID)
public class MMMMM {

    public static final String MODID = "mmmmm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MMMMM(ModContainer modContainer) {
        LOGGER.info("Initializing MMMMM...");

        // Register configuration
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info("MMMMM initialized.");

        NeoForge.EVENT_BUS.addListener(RegisterCommands::onRegisterCommands);
    }
}
