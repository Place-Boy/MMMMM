package com.mmmmm.mixin;

import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManageServerScreen.class)
public abstract class EditServerScreenMixin {

    @Unique
    private EditBox customField;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ManageServerScreen screen = (ManageServerScreen) (Object) this;

        screen.children().stream()
                .filter(c -> c instanceof EditBox)
                .map(c -> (EditBox) c)
                .forEach(editBox -> {
                    editBox.setY(editBox.getY() - 30);
                });

        // Move the resource pack prompt down
        screen.children().stream()
                .filter(c -> c instanceof CycleButton)
                .map(c -> (CycleButton<?>) c)
                .filter(button -> button.getMessage().getString().contains("Resource"))
                .forEach(button -> button.setY(button.getY() + 18)); // Adjust value as needed

        customField = new EditBox(
                mc.font,
                mc.getWindow().getGuiScaledWidth() / 2 - 100,
                mc.getWindow().getGuiScaledHeight() / 4 + 60,
                200, 20,
                Component.literal("Custom Field")
        );
        customField.setMaxLength(100);

        EditServerScreenAccessor accessor = (EditServerScreenAccessor) screen;
        String serverIP = accessor.getServerData().ip;
        String existingMetadata = ServerMetadata.getMetadata(serverIP);
        if (!existingMetadata.isBlank()) {
            customField.setValue(existingMetadata);
        }

        ((com.mmmmm.mixin.ScreenInvoker) this).invokeAddDrawableChild(customField);
    }

    @Inject(method = "onAdd", at = @At("TAIL"))
    private void onSaveCustomField(CallbackInfo ci) {
        if (customField != null) {
            String customValue = customField.getValue();
            ManageServerScreen screen = (ManageServerScreen) (Object) this;
            String serverIP = ((EditServerScreenAccessor) screen).getServerData().ip;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }
}