package com.mmmmm;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(MMMMM.MODID)
public class MMMMM {

    public static final String MODID = "mmmmm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MMMMM() {
        LOGGER.info("Initializing MMMMM...");

        // Add this in your mod initialization (e.g., in MMMMM.java constructor)
        try {
            Class.forName("net.minecraft.client.gui.screens.EditServerScreen");
            MMMMM.LOGGER.info("EditServerScreen is present!");
        } catch (ClassNotFoundException e) {
            MMMMM.LOGGER.error("EditServerScreen NOT found!");
        }

        // Register configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("MMMMM initialized.");

        MinecraftForge.EVENT_BUS.addListener(RegisterCommands::onRegisterCommands);
    }
}