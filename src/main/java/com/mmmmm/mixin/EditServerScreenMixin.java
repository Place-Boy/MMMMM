package com.mmmmm.mixin;

import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(EditServerScreen.class)
public abstract class EditServerScreenMixin {

    @Shadow public abstract void onClose();

    private int[] labelYPositions = new int[2]; // 0 = Server Name Y, 1 = Server Address Y
    private EditBox customField;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        EditServerScreen screen = (EditServerScreen) (Object) this;

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
            EditServerScreen screen = (EditServerScreen) (Object) this;
            String serverIP = ((EditServerScreenAccessor) screen).getServerData().ip;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void redirectDrawString(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int color) {
        // Prevent the original labels from being drawn by skipping the call
        String labelText = text.getString();
        if (!labelText.equals("Server Name") && !labelText.equals("Server Address")) {
            graphics.drawString(font, text, x, y, color); // Allow other text to render
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - 100;
        // Draw the Server Name and Server Address labels
        //graphics.fill(x - 2, labelYPositions[0] + 7, x + 200, labelYPositions[0] + 21, 0xFF0000FF); // Blue background
        graphics.drawString(Minecraft.getInstance().font, Component.literal("Server Name"), x, labelYPositions[0], 0xFFFFFFFF);
        graphics.drawString(Minecraft.getInstance().font, Component.literal("Server Address"), x, labelYPositions[1], 0xFFFFFFFF);

        // Draw the Download URL label
        graphics.drawString(Minecraft.getInstance().font, Component.literal("Download URL"), x, Minecraft.getInstance().getWindow().getGuiScaledHeight() / 4 + 50, 0xFFFFFFFF);
    }
}