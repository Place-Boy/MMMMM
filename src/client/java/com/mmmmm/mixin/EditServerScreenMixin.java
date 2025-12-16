package com.mmmmm.mixin;

import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManageServerScreen.class)
public abstract class EditServerScreenMixin {

    private int[] labelYPositions = new int[2]; // 0 = Server Name Y, 1 = Server Address Y
    private EditBox customField;

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ManageServerScreen screen = (ManageServerScreen) (Object) this;

        int[] index = {0};
        screen.children().stream()
                .filter(c -> c instanceof EditBox)
                .map(c -> (EditBox) c)
                .forEach(editBox -> {
                    editBox.setY(editBox.getY() - 30);
                    if (index[0] < 2) {
                        labelYPositions[index[0]++] = editBox.getY() - 10;
                    }
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
                net.minecraft.network.chat.Component.literal("Custom Field")
        );
        customField.setMaxLength(100);

        EditServerScreenAccessor accessor = (EditServerScreenAccessor) screen;
        String serverIP = accessor.getServer().ip;
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
            String serverIP = ((EditServerScreenAccessor) screen).getServer().ip;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ManageServerScreen screen = (ManageServerScreen) (Object) this;
        Font font = net.minecraft.client.Minecraft.getInstance().font;

        if (labelYPositions[0] != 0) {
            String label = "Server Name";
            int x = (screen.width - font.width(Component.literal(label))) / 2;
            context.drawString(font, Component.literal(label), x, labelYPositions[0], 0xFFFFFFFF, true);
        }
        if (labelYPositions[1] != 0) {
            String label = "Server Address";
            int x = (screen.width - font.width(Component.literal(label))) / 2;
            context.drawString(font, Component.literal(label), x, labelYPositions[1], 0xFFFFFFFF, true);
        }

        // Download URL label
        String downloadLabel = "Download URL";
        int x = (screen.width - font.width(Component.literal(downloadLabel))) / 2;
        context.drawString(font, Component.literal(downloadLabel), x, screen.height / 4 + 50, 0xFFFFFFFF, true);
    }

}