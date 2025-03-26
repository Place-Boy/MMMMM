package com.example.examplemod.client;

import com.example.examplemod.Config;
import com.example.examplemod.ExampleMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.Post;
import net.neoforged.bus.api.SubscribeEvent;

import static com.mojang.text2speech.Narrator.LOGGER;

/**
 * Handles client-side events for ExampleMod.
 */
@EventBusSubscriber(modid = ExampleMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    /**
     * Adds a custom button to the multiplayer screen.
     */
    @SubscribeEvent
    public static void onMultiplayerScreenRender(Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen screen) {
            // Create the button
            Button updateCheckButton = createUpdateCheckButton();

            // Add the button to the screen
            screen.renderables.add(updateCheckButton);
        }
    }

    /**
     * Creates the "Check for Updates" button.
     */
    private static Button createUpdateCheckButton() {
        final int buttonX = 10; // Place it at a default position (10 pixels from the left).
        final int buttonY = 10; // Place it at a default position (10 pixels from the top).

        return Button.builder(Component.literal("Check for Mod Updates"), button -> {
            LOGGER.info("Button clicked! Triggering update check...");
            onButtonClicked();
        }).bounds(buttonX, buttonY, 150, 20).build();
    }

    /**
     * Logic to handle what happens when the button is clicked.
     */
    public static void onButtonClicked() {
        LOGGER.info("Update check button was pressed!");

        if (isPlayerAvailable()) {
            sendPlayerMessage("Checking for updates...");
            // Simulate an action for the mod download process (omitted here for simplicity)
            LOGGER.info("Starting the mod download process...");
        } else {
            LOGGER.warn("Player is not available. Unable to proceed with the update check.");
        }
    }

    /**
     * Checks if the player is available in the current context.
     */
    private static boolean isPlayerAvailable() {
        return net.minecraft.client.Minecraft.getInstance().player != null;
    }

    /**
     * Sends a system message to the player.
     */
    private static void sendPlayerMessage(String message) {
        net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
    }
}