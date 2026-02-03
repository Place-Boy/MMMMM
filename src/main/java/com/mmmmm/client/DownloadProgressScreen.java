package com.mmmmm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadProgressScreen extends Screen {

    private volatile String downloadSource; // Use the correct download source
    private volatile String downloadLabel; // Current download label (mods/config)
    private volatile int progress = 0; // Progress percentage (0-100)
    private volatile String downloadSpeed = "0 KB/s"; // Download speed
    private volatile String estimatedTimeRemaining = ""; // Estimated time remaining
    private Button cancelButton; // Cancel button
    private volatile boolean isProcessing = false; // Indicates if processing is in progress
    private volatile String processingTitle = ""; // Message shown during processing
    private volatile String processingDetail = ""; // Extra info for processing
    private volatile int processingProgress = 0; // Progress for processing phase
    private volatile boolean processingHasProgress = false; // Whether processing progress is known
    private final Screen returnScreen;
    private volatile boolean showSummary = false;
    private volatile String summaryTitle = "Update complete";
    private volatile List<String> summaryLines = Collections.emptyList();


    public DownloadProgressScreen(String downloadLabel, String downloadSource, Screen returnScreen) {
        super(Component.literal("Downloading Update"));
        this.downloadLabel = downloadLabel == null || downloadLabel.isBlank() ? "files" : downloadLabel;
        this.downloadSource = downloadSource == null || downloadSource.isBlank() ? "server" : downloadSource;
        this.returnScreen = returnScreen;
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
            if (!showSummary) {
                isCancelled = true; // Signal cancellation
            }
            minecraft.execute(() -> minecraft.setScreen(returnScreen)); // Return to the previous screen
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
        this.isProcessing = false;
        this.processingTitle = "";
        this.processingDetail = "";
        this.processingProgress = 0;
        this.processingHasProgress = false;
        this.showSummary = false;
        this.summaryTitle = "Update complete";
        this.summaryLines = Collections.emptyList();
        if (cancelButton != null) {
            cancelButton.setMessage(Component.literal("Cancel"));
        }
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
        startProcessing(extractionMessage, "Last download speed: " + downloadSpeed);
    }

    public void startProcessing(String title, String detail) {
        updateProcessing(title, detail, 0, false);
    }

    public void updateProcessing(String title, String detail, int progress, boolean hasProgress) {
        this.isProcessing = true;
        this.processingTitle = title == null ? "" : title;
        this.processingDetail = detail == null ? "" : detail;
        this.processingProgress = Math.min(100, Math.max(0, progress));
        this.processingHasProgress = hasProgress;
    }

    public void showSummary(String title, List<String> lines) {
        this.showSummary = true;
        this.summaryTitle = title == null || title.isBlank() ? "Update complete" : title;
        if (lines == null || lines.isEmpty()) {
            this.summaryLines = Collections.emptyList();
        } else {
            this.summaryLines = Collections.unmodifiableList(new ArrayList<>(lines));
        }
        this.isProcessing = false;
        this.processingTitle = "";
        this.processingDetail = "";
        this.progress = 100;
        if (cancelButton != null) {
            cancelButton.setMessage(Component.literal("Close"));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        if (showSummary) {
            renderSummary(guiGraphics);
            return;
        }

        if (isProcessing) {
            renderProcessing(guiGraphics);
            return;
        }

        // Draw the title with the correct download source
        guiGraphics.drawCenteredString(this.font, "Downloading " + downloadLabel + " from " + downloadSource, this.width / 2, 20, 0xFFFFFF);

        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;

        // Draw the download speed above the progress bar
        guiGraphics.drawCenteredString(this.font, downloadSpeed, this.width / 2, barY - 30, 0xFFFFFF);
        // Draw estimated time remaining if available
        if (!estimatedTimeRemaining.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "ETA: " + estimatedTimeRemaining, this.width / 2, barY - 55, 0xFFFFFF);
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

    private void renderProcessing(GuiGraphics guiGraphics) {
        String title = processingTitle == null || processingTitle.isBlank()
                ? "Processing update..."
                : processingTitle;
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, 20, 0xFFFFFF);

        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;

        if (processingDetail != null && !processingDetail.isBlank()) {
            guiGraphics.drawCenteredString(this.font, processingDetail, this.width / 2, barY - 30, 0xFFFFFF);
        }

        // Draw the progress bar background
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFAAAAAA); // Gray background

        int progressWidth = processingHasProgress
                ? (int) (barWidth * (processingProgress / 100.0))
                : 0;
        guiGraphics.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00); // Green foreground

        String progressLabel = processingHasProgress ? (processingProgress + "%") : "...";
        guiGraphics.drawCenteredString(this.font, progressLabel, this.width / 2, barY + 5, 0xFFFFFF);
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(this.font, summaryTitle, this.width / 2, 20, 0xFFFFFF);

        int maxWidth = Math.max(200, this.width - 40);
        int y = (this.height / 2) - 40;

        if (summaryLines.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No details available.", this.width / 2, y, 0xFFFFFF);
            return;
        }

        for (String line : summaryLines) {
            for (var wrapped : this.font.split(Component.literal(line), maxWidth)) {
                guiGraphics.drawCenteredString(this.font, wrapped, this.width / 2, y, 0xFFFFFF);
                y += 12;
            }
            y += 4;
        }
    }
}
