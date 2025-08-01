package com.mmmmm.mixin;

import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(AddServerScreen.class)
public abstract class EditServerScreenMixin {

    @Shadow public abstract void onClose();

    private int[] labelYPositions = new int[2]; // 0 = Server Name Y, 1 = Server Address Y
    private EditBox customField;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AddServerScreen screen = (AddServerScreen) (Object) this;

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
            AddServerScreen screen = (AddServerScreen) (Object) this;
            String serverIP = ((EditServerScreenAccessor) screen).getServerData().ip;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = MinecraftClient.getInstance().getWindow().getGuiScaledWidth() / 2 - 100;
        if (labelYPositions[0] != 0) {
            graphics.drawString(Minecraft.getInstance().font, Component.literal("Server Name"), x, labelYPositions[0], 0xA0A0A0);
        }
        if (labelYPositions[1] != 0) {
            graphics.drawString(Minecraft.getInstance().font, Component.literal("Server Address"), x, labelYPositions[1], 0xA0A0A0);
        }
        graphics.drawString(Minecraft.getInstance().font, Component.literal("Download URL"), x, Minecraft.getInstance().getWindow().getGuiScaledHeight() / 4 + 50, 0xA0A0A0);
    }

    // Redirect original label draw call for "Server Name"
    @Redirect(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
            ordinal = 0))
    private int skipNameLabel(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int color) {
        return 0;
    }

    // Redirect original label draw call for "Server Address"
    @Redirect(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
            ordinal = 1))
    private int skipAddressLabel(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int color) {
        return 0;
    }
}