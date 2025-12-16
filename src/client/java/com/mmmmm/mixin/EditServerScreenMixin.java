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
import org.spongepowered.asm.mixin.Shadow;
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
        String serverIP = accessor.getServerData().ip;
        String existingMetadata = ServerMetadata.getMetadata(serverIP);
        if (!existingMetadata.isBlank()) {
            customField.setValue(existingMetadata);
        }

        ((com.mmmmm.mixin.ScreenInvoker) this).invokeAddDrawableChild(customField);
    }

    @Inject(method = "addAndClose", at = @At("TAIL"))
    private void onSaveCustomField(CallbackInfo ci) {
        if (customField != null) {
            String customValue = customField.getText();
            ManageServerScreen screen = (ManageServerScreen) (Object) this;
            String serverIP = ((EditServerScreenAccessor) screen).getServerData().ip;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawTextWithShadow(Lnet/minecraft/client/font/Font;Lnet/minecraft/text/Text;III)V"))
    private void redirectDrawLabel(GuiGraphics context, Font Font, net.minecraft.network.chat.Component text, int x, int y, int color) {
        // Skip original calls for the first two labels
        if (text.getString().contains("Server Name") || text.getString().contains("Server Address")) {
            return;
        }
        // Draw other labels normally
        context.drawTextWithShadow(Font, text, x, y, color);
    }

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = Minecraft.getInstance().getWindow().getScaledWidth() / 2 - 100;
        if (labelYPositions[0] != 0) {
            context.drawText(Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal("Server Name"), x, labelYPositions[0], 0xFFFFFFFF, true);
        }
        if (labelYPositions[1] != 0) {
            context.drawText(Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal("Server Address"), x, labelYPositions[1], 0xFFFFFFFF, true);
        }
        context.drawText(Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal("Download URL"), x, Minecraft.getInstance().getWindow().getScaledHeight() / 4 + 50, 0xFFFFFFFF, true);
    }
}