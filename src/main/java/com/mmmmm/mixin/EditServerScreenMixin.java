package com.mmmmm.mixin;

import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(ManageServerScreen.class)
public abstract class EditServerScreenMixin {

    @Shadow public abstract void onClose();

    private int[] labelYPositions = new int[2]; // 0 = Server Name Y, 1 = Server Address Y
    private EditBox customField;

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

        screen.children().stream()
                .filter(c -> c instanceof net.minecraft.client.gui.components.CycleButton)
                .map(c -> (net.minecraft.client.gui.components.CycleButton) c)
                .filter(button -> button.getMessage().getString().contains("Resource"))
                .forEach(button -> button.setY(button.getY() + 18));

        customField = new EditBox(
                mc.font,
                mc.getWindow().getGuiScaledWidth() / 2 - 100,
                mc.getWindow().getGuiScaledHeight() / 4 + 60,
                200, 20,
                Component.literal("Custom Field")
        );
        customField.setMaxLength(100);

        // Fill the custom field if metadata exists
        EditServerScreenAccessor accessor = (EditServerScreenAccessor) screen;
        String serverIP = accessor.getServerData().ip;
        String existingMetadata = ServerMetadata.getMetadata(serverIP);
        if (!existingMetadata.isBlank()) {
            customField.setValue(existingMetadata);
        }

        ((ScreenAccessorMixin) (Object) this).invokeAddRenderableWidget(customField);
    }

    @Inject(method = "onAdd", at = @At("TAIL"))
    private void onSaveCustomField(CallbackInfo ci) {
        LOGGER.info("onSaveCustomField called");
        if (customField != null) {
            String customValue = customField.getValue();
            ManageServerScreen screen = (ManageServerScreen) (Object) this;
            String serverIP = ((EditServerScreenAccessor) screen).getServerData().ip;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - 100;
        if (labelYPositions[0] != 0) {
            graphics.centeredText(Minecraft.getInstance().font, Component.literal("Server Name"), x, labelYPositions[0], 0xA0A0A0);
        }
        if (labelYPositions[1] != 0) {
            graphics.centeredText(Minecraft.getInstance().font, Component.literal("Server Address"), x, labelYPositions[1], 0xA0A0A0);
        }
        graphics.centeredText(Minecraft.getInstance().font, Component.literal("Download URL"), x, Minecraft.getInstance().getWindow().getGuiScaledHeight() / 4 + 50, 0xA0A0A0);
    }

    // Redirect original label draw call for "Server Name"
    @Redirect(method = "extractRenderState", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
            ordinal = 0))
    private void skipNameLabel(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int color) {
        // Do nothing
    }

    // Redirect original label draw call for "Server Address"
    @Redirect(method = "extractRenderState", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
            ordinal = 1))
    private void skipAddressLabel(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int color) {
        // Do nothing
    }
}