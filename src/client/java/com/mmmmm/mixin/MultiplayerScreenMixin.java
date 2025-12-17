package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static com.mmmmm.MMMMM.LOGGER;

// TODO(Ravel): can not resolve target class MultiplayerScreen
// TODO(Ravel): can not resolve target class MultiplayerScreen
@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin {

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        JoinMultiplayerScreen screen = (JoinMultiplayerScreen) (Object) this;

        ServerList serverList = new ServerList(Minecraft.getInstance());
        serverList.load();

        int buttonX = screen.width - 55;
        int buttonY = 40;
        int buttonSpacing = 35;
        int maxHeight = screen.height - 50;

        List<Button> buttonsToAdd = new ArrayList<>();

        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerData server = serverList.get(i);
            Button serverButton = createServerButton(buttonX, yOffset, server);
            buttonsToAdd.add(serverButton);
        }

        Minecraft.getInstance().execute(() -> {
            for (Button button : buttonsToAdd) {
                ((ScreenInvoker) screen).invokeAddDrawableChild(button);
            }
        });
    }

    private Button createServerButton(int x, int y, ServerData server) {
        return Button.builder(
                Component.literal("Update"),
                (btn) -> {
                    String downloadIP = ServerMetadata.getMetadata(server.ip); // Fetch the correct download IP
                    if (downloadIP == null || downloadIP.isBlank()) {
                        LOGGER.error("No download IP found for server: {}", server.ip);
                        return;
                    }
                    ClientEventHandlers.downloadAndProcessMod(downloadIP); // Use the download IP
                }
        ).pos(x, y).size(50, 20).build();
    }
}