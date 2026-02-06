package com.mmmmm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class DownloadProgressScreen extends Screen {

    private final String serverUpdateIP;

    // Progress / extraction state
    private int progress = 0;
    private String downloadSpeed = "0 KB/s";
    private String estimatedTimeRemaining = "";
    private boolean isExtracting = false;
    private String extractionMessage = "";

    // Flow state: false = selection step, true = progress step
    private boolean selectionConfirmed = false;
    private volatile boolean isCancelled = false;

    // Widgets
    private Button cancelButton;
    private Button startDownloadButton;
    private Checkbox configCheckbox;
    private Checkbox kubejsCheckbox;
    private Checkbox modsCheckbox;

    // Selections
    private boolean downloadConfig = true;
    private boolean downloadKubejs = true;
    private boolean downloadMods = true;

    // Callback that the client code can use to start the download with the chosen options
    @FunctionalInterface
    public interface DownloadStarter {
        void start(boolean config, boolean kubejs, boolean mods, DownloadProgressScreen screen);
    }

    private final DownloadStarter downloadStarter;

    public DownloadProgressScreen(String serverIP, DownloadStarter downloadStarter) {
        super(Component.literal("Downloading Mods"));
        this.serverUpdateIP = serverIP;
        this.downloadStarter = downloadStarter;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        if (!selectionConfirmed) {
            initSelectionWidgets();
        } else {
            initProgressWidgets();
        }
    }

    private void initSelectionWidgets() {
        int checkboxWidth = 150;
        int startY = this.height / 2 - 40;
        int centerX = this.width / 2 - checkboxWidth / 2;

        // Config checkbox
        configCheckbox = Checkbox.builder(Component.literal("Download config"), this.font)
                .pos(centerX, startY)
                .selected(downloadConfig)
                .build();

        // KubeJS checkbox
        kubejsCheckbox = Checkbox.builder(Component.literal("Download kubejs"), this.font)
                .pos(centerX, startY + 20)
                .selected(downloadKubejs)
                .build();

        // Mods checkbox
        modsCheckbox = Checkbox.builder(Component.literal("Download mods"), this.font)
                .pos(centerX, startY + 40)
                .selected(downloadMods)
                .build();

        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = startY + 70;

        startDownloadButton = Button.builder(Component.literal("Start Download"), (button) -> {
            downloadConfig = configCheckbox.selected();
            downloadKubejs = kubejsCheckbox.selected();
            downloadMods = modsCheckbox.selected();

            // Require at least one selection
            if (!downloadConfig && !downloadKubejs && !downloadMods) {
                return;
            }

            selectionConfirmed = true;
            this.init();

            if (downloadStarter != null) {
                downloadStarter.start(downloadConfig, downloadKubejs, downloadMods, this);
            }
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(configCheckbox);
        this.addRenderableWidget(kubejsCheckbox);
        this.addRenderableWidget(modsCheckbox);
        this.addRenderableWidget(startDownloadButton);

        // Cancel button in selection step
        int cancelWidth = 100;
        int cancelHeight = 20;
        int cancelX = (this.width - cancelWidth) / 2;
        int cancelY = buttonY + 30;

        cancelButton = Button.builder(Component.literal("Cancel"), (button) -> {
            isCancelled = true;
            minecraft.execute(() -> minecraft.setScreen(new TitleScreen()));
        }).bounds(cancelX, cancelY, cancelWidth, cancelHeight).build();

        this.addRenderableWidget(cancelButton);
    }

    private void initProgressWidgets() {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = (this.height / 2) + 50;

        cancelButton = Button.builder(Component.literal("Cancel"), (button) -> {
            isCancelled = true;
            minecraft.execute(() -> minecraft.setScreen(new TitleScreen()));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(cancelButton);
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public boolean shouldDownloadConfig() {
        return downloadConfig;
    }

    public boolean shouldDownloadKubejs() {
        return downloadKubejs;
    }

    public boolean shouldDownloadMods() {
        return downloadMods;
    }

    public void updateProgress(int progress, String downloadSpeed, String estimatedTimeRemaining) {
        this.progress = Math.min(100, Math.max(0, progress));
        this.downloadSpeed = downloadSpeed;
        this.estimatedTimeRemaining = estimatedTimeRemaining;
    }

    public void startExtraction(String extractionMessage) {
        this.isExtracting = true;
        this.extractionMessage = extractionMessage + " (Last download speed: " + downloadSpeed + ")";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        guiGraphics.drawCenteredString(
                this.font,
                selectionConfirmed
                        ? "Downloading from " + serverUpdateIP
                        : "Select what to download from " + serverUpdateIP,
                this.width / 2,
                20,
                0xFFFFFF
        );

        if (!selectionConfirmed) {
            return;
        }

        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;

        if (isExtracting) {
            guiGraphics.drawCenteredString(this.font, extractionMessage, this.width / 2, barY - 30, 0xFFFFFF);
        } else {
            guiGraphics.drawCenteredString(this.font, downloadSpeed, this.width / 2, barY - 30, 0xFFFFFF);
            if (!estimatedTimeRemaining.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, "ETA: " + estimatedTimeRemaining, this.width / 2, barY - 55, 0xFFFFFF);
            }
        }

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFAAAAAA);
        int progressWidth = (int) (barWidth * (progress / 100.0));
        guiGraphics.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00);
        guiGraphics.drawCenteredString(this.font, progress + "%", this.width / 2, barY + 5, 0xFFFFFF);
    }
}

