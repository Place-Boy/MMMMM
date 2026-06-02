package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface JoinMultiplayerScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof JoinMultiplayerScreen) {
            for (Button btn : ClientEventHandlers.getServerButtons()) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    if (btn.mouseClicked(mouseX, mouseY, button)) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }
}