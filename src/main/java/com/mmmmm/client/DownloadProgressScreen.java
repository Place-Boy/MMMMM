package com.mmmmm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class DownloadProgressScreen extends Screen {

    private final String serverIP;

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

        guiGraphics.drawCenteredString(this.font, "Downloading mods from " + serverIP, this.width / 2, 20, 0xFFFFFF);
    }
}