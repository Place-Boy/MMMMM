package com.mmmmm.client;

import com.mmmmm.MMMMM;

public class ClientInit {
    public static void checkEditServerScreen() {
        try {
            Class.forName("net.minecraft.client.gui.screens.EditServerScreen");
            MMMMM.LOGGER.info("EditServerScreen is present!");
        } catch (ClassNotFoundException e) {
            MMMMM.LOGGER.error("EditServerScreen NOT found!");
        }
    }
}