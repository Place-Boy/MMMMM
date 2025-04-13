package com.mmmmm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import static javax.swing.plaf.basic.BasicGraphicsUtils.drawString;

public class ServerAddEditScreen extends  {
    private final ServerData serverData;
    private EditBox portField;

    public ServerAddEditScreen(Screen parent, BooleanConsumer callback, ServerData serverData) {
        super(parent, callback, serverData);
        this.serverData = serverData;
    }

    @Override
    protected void init() {
        super.init();

        // Retrieve the existing download IP for the server
        String existingDownloadIP = ServerMetadataManager.getDownloadIP(serverData.ip);

        // Create the input box for the "Download IP" field
        this.portField = new EditBox(this.font, this.width / 2 - 100, this.height / 4 + 100, 200, 20, Component.literal("Download IP"));
        this.portField.setValue(existingDownloadIP); // Set the existing value
        this.portField.setMaxLength(128); // Optional: Set a max length for the input
        this.addRenderableWidget(this.portField); // Add the input box to the screen
    }

    @Override
    public void onClose() {
        String downloadIP = this.portField.getValue();
        ServerMetadataManager.setDownloadIP(serverData.ip, downloadIP);
        super.onClose();
    }


    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        // Render the label for the port field
        guiGraphics.drawString(this.font, "Modpack Download IP:", this.width / 2 - 100, this.height / 4 + 85, 0xA0A0A0, false);
    }

    public ServerData getServerData() {
        return this.serverData;
    }
}