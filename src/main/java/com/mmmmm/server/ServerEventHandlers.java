package com.mmmmm.server;

import com.mmmmm.MMMMM;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Handles server-side events.
 */
@EventBusSubscriber(modid = MMMMM.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
public class ServerEventHandlers {

    /**
     * Handle server-side setup tasks.
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        try {
            MMMMM.LOGGER.info("Performing common setup tasks.");
            // Initialize server-related tasks here, like starting file hosting server
            FileHostingServer.start();
        } catch (Exception e) {
            MMMMM.LOGGER.error("Failed to start file hosting server: ", e);
        }
    }
}
