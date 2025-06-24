package com.mmmmm.server;

import com.mmmmm.MMMMM;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Handles server-side events.
 */
@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
public class ServerEventHandlers {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        try {
            MMMMM.LOGGER.info("Performing common setup tasks.");
            FileHostingServer.start();
        } catch (Exception e) {
            MMMMM.LOGGER.error("Failed to start file hosting server: ", e);
        }
    }
}