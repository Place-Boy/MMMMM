package com.example.examplemod.client;

import com.example.examplemod.Config;
import com.example.examplemod.ExampleMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Handles client-side events for ExampleMod.
 */
@EventBusSubscriber(modid = ExampleMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ClientEventHandlers {

    /**
     * Adds a custom button to the Title Screen.
     */
    @SubscribeEvent
    public static void onMultiplayerScreenOpen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen multiplayerScreen) {
            Button customButton = Button.builder(
                            Component.literal("Check for Updates"),
                            button -> ExampleMod.LOGGER.info("Update check triggered!"))
                    .pos(Config.buttonX / 2 + 250, Config.buttonY / 4 - 20)
                    .size(100, 20)
                    .build();
            multiplayerScreen.renderables.add(customButton);
        }
    }
}
