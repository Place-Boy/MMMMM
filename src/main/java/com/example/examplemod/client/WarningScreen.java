package com.example.examplemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A simple screen that displays text and has a button to return to the Title Screen.
 */
public class WarningScreen extends Screen {

    private static final String WARNING_TEXT = "Please look at the config file to enter URL"; // Text to display

    /**
     * Constructor for the warning screen.
     */
    public WarningScreen(MutableComponent literal) {
        super(Component.literal("No URL entered!"));
    }

    @Override
    protected void init() {
        super.init();

        // Add a "Back to Title" button
        this.addRenderableWidget(Button.builder(Component.literal("Back to Title"), button -> {
            // Navigate to the Title Screen
            Minecraft.getInstance().setScreen(new TitleScreen());
        }).bounds(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        // Step 1: Render the blurred background
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);

        // Render buttons and other components
        super.render(guiGraphics, mouseX, mouseY, delta);

        // Step 2: Render the text and buttons on top of the background
        guiGraphics.drawCenteredString(
                this.font,
                WARNING_TEXT,
                this.width / 2,
                this.height / 2 - 10,
                0xFFFFFF // White text color
        );
    }
}