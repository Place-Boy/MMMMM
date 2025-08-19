package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin() { super(null); }

    @Inject(method = "resize", at = @At("TAIL"))
    private void onResize(net.minecraft.client.MinecraftClient client, int width, int height, CallbackInfo ci) {
        ClientEventHandlers.addUpdateButtons((MultiplayerScreen) (Object) this);
    }
}