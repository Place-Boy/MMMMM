package com.mmmmm.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenRenderMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Only run for JoinMultiplayerScreen
        Screen self = (Screen) (Object) this;
        if (!(self instanceof JoinMultiplayerScreen screen)) return;

        for (Button button : screen.children().stream()
                .filter(c -> c instanceof Button)
                .map(c -> (Button) c)
                .toList()) {
            button.render(context, mouseX, mouseY, delta); // Render buttons on top
        }
    }
}
