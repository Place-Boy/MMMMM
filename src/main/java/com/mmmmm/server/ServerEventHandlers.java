package com.mmmmm.server;

import com.mmmmm.MMMMM;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ServerEventHandlers {
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                MMMMM.LOGGER.info("Performing common setup tasks.");
                FileHostingServer.start();
            } catch (Exception e) {
                MMMMM.LOGGER.error("Failed to start file hosting server: ", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            com.mmmmm.server.FileHostingServer.stop();
        });
    }
}