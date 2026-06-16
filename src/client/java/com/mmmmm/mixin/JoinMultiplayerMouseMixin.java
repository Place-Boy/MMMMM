package com.mmmmm.mixin;

import com.mmmmm.client.ClientEventHandlers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface JoinMultiplayerMouseMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof JoinMultiplayerScreen) {
            for (Button btn : ClientEventHandlers.getServerButtons()) {
                if (btn.isMouseOver(event.x(), event.y())) {
                    if (btn.mouseClicked(event, doubleClick)) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }
}