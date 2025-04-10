package com.mmmmm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DownloadProgressScreen extends Screen {

    private final String serverIP;
    private int progress = 0; // Progress percentage (0-100)
    private String downloadSpeed = "0 KB/s"; // Download speed

    public DownloadProgressScreen(String serverIP) {
        super(Component.literal("Downloading Mods"));
        this.serverIP = serverIP;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Draw the title
        guiGraphics.drawCenteredString(this.font, "Downloading mods from " + serverIP, this.width / 2, 20, 0xFFFFFF);

        // Draw the progress bar background
        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFAAAAAA); // Gray background

        // Draw the progress bar foreground
        int progressWidth = (int) (barWidth * (progress / 100.0));
        guiGraphics.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00); // Green foreground

        // Draw the progress percentage
        guiGraphics.drawCenteredString(this.font, progress + "%", this.width / 2, barY + 5, 0xFFFFFF);

        // Draw the download speed below the progress bar
        guiGraphics.drawCenteredString(this.font, downloadSpeed, this.width / 2, barY + 30, 0xFFFFFF);
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