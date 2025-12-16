package com.mmmmm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class DownloadProgressScreen extends Screen {

    private final String serverUpdateIP;
    private int progress = 0;
    private String downloadSpeed = "0 KB/s";
    private Button cancelButton;
    private volatile boolean isCancelled = false;

    public DownloadProgressScreen(String serverIP) {
        super(Component.literal("Downloading Mods"));
        this.serverUpdateIP = serverIP;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = (this.height / 2) + 50;

        cancelButton = Button.builder(
                Component.literal("Cancel"),
                (button) -> {
                    isCancelled = true;
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreen(new TitleScreen()));
                }
        ).pos(buttonX, buttonY).size(buttonWidth, buttonHeight).build();

        this.addRenderableWidget(cancelButton);
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Draw the title
        context.drawCenteredString(this.font,
                "Downloading mods from " + serverUpdateIP,
                this.width / 2, 20, 0xFFFFFFFF);

        int barWidth = 200;
        int barHeight = 20;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2;

        // Draw download speed
        context.drawCenteredString(this.font, downloadSpeed, this.width / 2, barY - 30, 0xFFFFFFFF);

        // Draw progress bar background
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFAAAAAA);

        // Draw progress bar foreground
        int progressWidth = (int) (barWidth * (progress / 100.0));
        context.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00);

        // Draw progress percentage
        context.drawCenteredString(this.font, progress + "%", this.width / 2, barY + 5, 0xFFFFFFFF);
    }

    public void updateProgress(int progress, String downloadSpeed) {
        this.progress = Math.min(100, Math.max(0, progress));
        this.downloadSpeed = downloadSpeed;
    }
}