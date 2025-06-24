package com.mmmmm.mixin;

import com.mmmmm.MMMMM;
import com.mmmmm.client.ClientEventHandlers;
import com.mmmmm.mixin.ScreenAccessorMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {

    private Button updateButton;

    @Inject(method = "init", at = @At("HEAD"))
    private void addUpdateButtons(CallbackInfo ci) {
        MMMMM.LOGGER.info("JoinMultiplayerScreenMixin initialized and init method called.");
        addCustomButtons();
        MMMMM.LOGGER.info("Custom buttons added to JoinMultiplayerScreen.");
    }

    private void addCustomButtons() {
        JoinMultiplayerScreen screen = (JoinMultiplayerScreen) (Object) this;
        ServerList serverList = new ServerList(Minecraft.getInstance());
        serverList.load();

        int buttonX = screen.width - 55;
        int buttonY = 50;
        int buttonSpacing = 24;
        int maxHeight = screen.height - 50;

        List<Button> buttonsToAdd = new ArrayList<>();
        for (int i = 0; i < serverList.size(); i++) {
            int yOffset = buttonY + (i * buttonSpacing);
            if (yOffset + 20 > maxHeight) break;

            ServerData server = serverList.get(i);
            Button serverButton = ClientEventHandlers.createServerButton(buttonX, yOffset, server);
            buttonsToAdd.add(serverButton);
        }
        for (Button button : buttonsToAdd) {
            ((ScreenAccessorMixin) screen).invokeAddRenderableWidget(button);
            MMMMM.LOGGER.info("Button added to JoinMultiplayerScreen via mixin");
            ((List<net.minecraft.client.gui.components.events.GuiEventListener>) screen.children()).add((net.minecraft.client.gui.components.events.GuiEventListener) button);
        }
    }
}