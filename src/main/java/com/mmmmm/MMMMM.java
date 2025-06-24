package com.mmmmm;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
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
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> com.mmmmm.client.ClientInit::checkEditServerScreen);
        // Register configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "mmmmm-common.toml");
        LOGGER.info("MMMMM initialized.");

        MinecraftForge.EVENT_BUS.addListener(RegisterCommands::onRegisterCommands);
    }
}