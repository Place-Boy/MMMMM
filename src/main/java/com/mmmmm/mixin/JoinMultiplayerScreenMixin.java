package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
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

@Mixin(JoinMultiplayerScreen.class)
public class JoinMultiplayerScreenMixin {

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
                ((ScreenAccessorMixin) screen).invokeAddRenderableWidget(button);
            }
        });
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        JoinMultiplayerScreen screen = (JoinMultiplayerScreen) (Object) this;

        for (Button button : screen.children().stream()
                .filter(c -> c instanceof Button)
                .map(c -> (Button) c)
                .toList()) {
            button.render(graphics, mouseX, mouseY, delta); // Render buttons on top
        }
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
        ).bounds(x, y, 50, 20).build();
    }
}