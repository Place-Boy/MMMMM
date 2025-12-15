package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static com.mmmmm.MMMMM.LOGGER;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {

    @Unique
    private final List<ButtonWidget> mmmmm$updateButtons = new ArrayList<>();

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MultiplayerScreen screen = (MultiplayerScreen) (Object) this;
        this.mmmmm$updateButtons.clear();

        ServerList serverList = new ServerList(MinecraftClient.getInstance());
        serverList.loadFile();

        int buttonX = screen.width - 55;
        int buttonY = 40;
        int buttonSpacing = 35;
        int maxHeight = screen.height - 50;

        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerInfo server = serverList.get(i);
            ButtonWidget serverButton = createServerButton(buttonX, yOffset, server);
            this.mmmmm$updateButtons.add(serverButton);
            ((ScreenInvoker) screen).invokeAddDrawableChild(serverButton);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        for (ButtonWidget button : this.mmmmm$updateButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
    }

    @Unique
    private ButtonWidget createServerButton(int x, int y, ServerInfo server) {
        return ButtonWidget.builder(
                Text.literal("Update"),
                (btn) -> {
                    String downloadIP = ServerMetadata.getMetadata(server.address); // Fetch the correct download IP
                    if (downloadIP == null || downloadIP.isBlank()) {
                        LOGGER.error("No download IP found for server: {}", server.address);
                        return;
                    }
                    ClientEventHandlers.downloadAndProcessMod(downloadIP); // Use the download IP
                }
        ).dimensions(x, y, 50, 20).build();
    }
}