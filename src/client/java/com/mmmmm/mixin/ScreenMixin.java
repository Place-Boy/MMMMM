package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "resize", at = @At("TAIL"))
    private void onResize(net.minecraft.client.MinecraftClient client, int width, int height, CallbackInfo ci) {
        if ((Object) this instanceof MultiplayerScreen ms) {
            ClientEventHandlers.addUpdateButtons(ms);
        }
    }
}