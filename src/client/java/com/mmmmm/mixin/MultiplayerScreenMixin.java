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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static com.mmmmm.MMMMM.LOGGER;

// TODO(Ravel): can not resolve target class MultiplayerScreen
// TODO(Ravel): can not resolve target class MultiplayerScreen
@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        MultiplayerScreen screen = (MultiplayerScreen) (Object) this;

        ServerList serverList = new ServerList(MinecraftClient.getInstance());
        serverList.loadFile();

        int buttonX = screen.width - 55;
        int buttonY = 40;
        int buttonSpacing = 35;
        int maxHeight = screen.height - 50;

        List<ButtonWidget> buttonsToAdd = new ArrayList<>();

        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerInfo server = serverList.get(i);
            ButtonWidget serverButton = createServerButton(buttonX, yOffset, server);
            buttonsToAdd.add(serverButton);
        }

        MinecraftClient.getInstance().execute(() -> {
            for (ButtonWidget button : buttonsToAdd) {
                ((ScreenInvoker) screen).invokeAddDrawableChild(button);
            }
        });
    }

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MultiplayerScreen screen = (MultiplayerScreen) (Object) this;

        for (ButtonWidget button : screen.children().stream()
                .filter(c -> c instanceof ButtonWidget)
                .map(c -> (ButtonWidget) c)
                .toList()) {
            button.render(context, mouseX, mouseY, delta); // Render buttons on top
        }
    }

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