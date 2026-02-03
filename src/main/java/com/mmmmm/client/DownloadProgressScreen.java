package com.mmmmm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class DownloadProgressScreen extends Screen {

    private volatile String downloadSource; // Use the correct download source
    private volatile String downloadLabel; // Current download label (mods/config)
    private volatile int progress = 0; // Progress percentage (0-100)
    private volatile String downloadSpeed = "0 KB/s"; // Download speed
    private volatile String estimatedTimeRemaining = ""; // Estimated time remaining
    private Button cancelButton; // Cancel button
    private volatile boolean isExtracting = false; // Indicates if extraction is in progress
    private volatile String extractionMessage = ""; // Message shown during extraction


    public DownloadProgressScreen(String downloadLabel, String downloadSource) {
        super(Component.literal("Downloading Update"));
        this.downloadLabel = downloadLabel == null || downloadLabel.isBlank() ? "files" : downloadLabel;
        this.downloadSource = downloadSource == null || downloadSource.isBlank() ? "server" : downloadSource;
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

    /**
     * Resets the UI for a new download.
     */
    public void startNewDownload(String downloadLabel, String downloadSource) {
        this.downloadLabel = downloadLabel == null || downloadLabel.isBlank() ? "files" : downloadLabel;
        this.downloadSource = downloadSource == null || downloadSource.isBlank() ? "server" : downloadSource;
        this.progress = 0;
        this.downloadSpeed = "0 KB/s";
        this.estimatedTimeRemaining = "";
        this.isExtracting = false;
        this.extractionMessage = "";
    }

    /**
     * Updates the progress bar, download speed, and estimated time remaining.
     *
     * @param progress      The current progress (0-100).
     * @param downloadSpeed The current download speed in KB/s.
     * @param estimatedTimeRemaining Estimated time remaining (optional).
     */
    public void updateProgress(int progress, String downloadSpeed, String estimatedTimeRemaining) {
        this.progress = Math.min(100, Math.max(0, progress)); // Clamp progress between 0 and 100
        this.downloadSpeed = downloadSpeed;
        this.estimatedTimeRemaining = estimatedTimeRemaining;
    }

    /**
     * Call this when extraction starts after download finishes.
     * Shows extraction info including last download speed.
     */
    public void startExtraction(String extractionMessage) {
        this.isExtracting = true;
        this.extractionMessage = extractionMessage + " (Last download speed: " + downloadSpeed + ")";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Draw the title with the correct download source
        guiGraphics.drawCenteredString(this.font, "Downloading " + downloadLabel + " from " + downloadSource, this.width / 2, 20, 0xFFFFFF);

        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;

        if (isExtracting) {
            guiGraphics.drawCenteredString(this.font, extractionMessage, this.width / 2, barY - 30, 0xFFFFFF);
        } else {
            // Draw the download speed above the progress bar
            guiGraphics.drawCenteredString(this.font, downloadSpeed, this.width / 2, barY - 30, 0xFFFFFF);
            // Draw estimated time remaining if available
            if (!estimatedTimeRemaining.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, "ETA: " + estimatedTimeRemaining, this.width / 2, barY - 55, 0xFFFFFF);
            }
        }

        // Draw the progress bar background
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFAAAAAA); // Gray background

        // Draw the progress bar foreground
        int progressWidth = (int) (barWidth * (progress / 100.0));
        guiGraphics.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00); // Green foreground

        // Draw the progress percentage
        guiGraphics.drawCenteredString(this.font, progress + "%", this.width / 2, barY + 5, 0xFFFFFF);

        // The cancel button is already positioned below the progress bar in the `init` method
    }
}
