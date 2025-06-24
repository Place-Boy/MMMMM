package com.mmmmm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class DownloadProgressScreen extends Screen {

    private final String serverUpdateIP; // Use the correct server update IP
    private int progress = 0; // Progress percentage (0-100)
    private String downloadSpeed = "0 KB/s"; // Download speed
    private Button cancelButton; // Cancel button


    public DownloadProgressScreen(String serverIP) {
        super(Component.literal("Downloading Mods"));
        this.serverUpdateIP = serverIP; // Set the correct server update IP
    }

    private volatile boolean isCancelled = false;

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = (this.height / 2) + 50;

        cancelButton = Button.builder(Component.literal("Cancel"), (button) -> {
            isCancelled = true; // Signal cancellation
            minecraft.execute(() -> minecraft.setScreen(new TitleScreen())); // Return to the title screen
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(cancelButton);
    }

    /**
     * Checks if the download has been cancelled.
     */
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Draw the title with the correct server update IP
        guiGraphics.drawCenteredString(this.font, "Downloading mods from " + serverUpdateIP, this.width / 2, 20, 0xFFFFFF);


        // Draw the download speed above the progress bar
        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;

        guiGraphics.drawCenteredString(this.font, downloadSpeed, this.width / 2, barY - 30, 0xFFFFFF);

        // Draw the progress bar background
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFAAAAAA); // Gray background

        // Draw the progress bar foreground
        int progressWidth = (int) (barWidth * (progress / 100.0));
        guiGraphics.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00); // Green foreground

        // Draw the progress percentage
        guiGraphics.drawCenteredString(this.font, progress + "%", this.width / 2, barY + 5, 0xFFFFFF);

        // The cancel button is already positioned below the progress bar in the `init` method
    }

    /**
     * Updates the progress bar and download speed.
     *
     * @param progress      The current progress (0-100).
     * @param downloadSpeed The current download speed in KB/s.
     */
    public void updateProgress(int progress, String downloadSpeed) {
        this.progress = Math.min(100, Math.max(0, progress)); // Clamp progress between 0 and 100
        this.downloadSpeed = downloadSpeed;
    }
}