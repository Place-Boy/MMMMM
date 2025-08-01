// Fabric mod main class
package com.mmmmm;

import com.mmmmm.server.ServerEventHandlers;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MMMMM implements ModInitializer {
    public static final String MODID = "mmmmm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing MMMMM...");
        Config.registerConfig();
        RegisterCommands.register();
        ServerEventHandlers.register(); // <-- Add this line
        LOGGER.info("MMMMM initialized.");
    }
}