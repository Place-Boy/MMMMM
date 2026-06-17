package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mmmmm.MMMMM.LOGGER;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.mmmmm$addCustomButtons();

        JoinMultiplayerScreen screen = (JoinMultiplayerScreen) (Object) this;

        this.serverSelectionList.setWidth(screen.width - 120);
        this.serverSelectionList.setX((screen.width - (screen.width - 120)) / 2);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void onRepositionElements(CallbackInfo ci) {
        this.mmmmm$addCustomButtons();
    }

    @Unique
    private void mmmmm$addCustomButtons() {
        ServerList serverList = new ServerList(Minecraft.getInstance());
        serverList.load();

        int buttonX = this.width - 55;
        int buttonY = 40;
        int buttonSpacing = 35;
        int maxHeight = this.height - 50;

        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerData server = serverList.get(i);
            Button serverButton = mmmmm$createServerButton(buttonX, yOffset, server);

            this.addRenderableWidget(serverButton);
        }
    }

    @Unique
    private Button mmmmm$createServerButton(int x, int y, ServerData server) {
        return Button.builder(
                Component.literal("Update"),
                (btn) -> {
                    String downloadIP = ServerMetadata.getMetadata(server.ip);
                    if (downloadIP == null || downloadIP.isBlank()) {
                        LOGGER.error("No download IP found for server: {}", server.ip);
                        return;
                    }
                    ClientEventHandlers.downloadAndProcessMod(downloadIP);
                }
        ).bounds(x, y, 50, 20).build();
    }
}