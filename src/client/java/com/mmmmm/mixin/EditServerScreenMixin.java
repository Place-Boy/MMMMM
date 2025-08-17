package com.mmmmm.mixin;

import com.mmmmm.client.ServerMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AddServerScreen.class)
public abstract class EditServerScreenMixin {

    @Shadow public abstract void close();

    private int[] labelYPositions = new int[2]; // 0 = Server Name Y, 1 = Server Address Y
    private TextFieldWidget customField;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AddServerScreen screen = (AddServerScreen) (Object) this;

        int[] index = {0};
        screen.children().stream()
                .filter(c -> c instanceof TextFieldWidget)
                .map(c -> (TextFieldWidget) c)
                .forEach(editBox -> {
                    editBox.setY(editBox.getY() - 30);
                    if (index[0] < 2) {
                        labelYPositions[index[0]++] = editBox.getY() - 10;
                    }
                });

        // Move the resource pack prompt down
        screen.children().stream()
                .filter(c -> c instanceof CyclingButtonWidget)
                .map(c -> (CyclingButtonWidget<?>) c)
                .filter(button -> button.getMessage().getString().contains("Resource"))
                .forEach(button -> button.setY(button.getY() + 18)); // Adjust value as needed

        customField = new TextFieldWidget(
                mc.textRenderer,
                mc.getWindow().getScaledWidth() / 2 - 100,
                mc.getWindow().getScaledHeight() / 4 + 60,
                200, 20,
                Text.literal("Custom Field")
        );
        customField.setMaxLength(100);

        EditServerScreenAccessor accessor = (EditServerScreenAccessor) screen;
        String serverIP = accessor.getServerData().address;
        String existingMetadata = ServerMetadata.getMetadata(serverIP);
        if (!existingMetadata.isBlank()) {
            customField.setText(existingMetadata);
        }

        ((com.mmmmm.mixin.ScreenInvoker) this).invokeAddDrawableChild(customField);
    }

    @Inject(method = "addAndClose", at = @At("TAIL"))
    private void onSaveCustomField(CallbackInfo ci) {
        if (customField != null) {
            String customValue = customField.getText();
            AddServerScreen screen = (AddServerScreen) (Object) this;
            String serverIP = ((EditServerScreenAccessor) screen).getServerData().address;
            ServerMetadata.setMetadata(serverIP, customValue);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = MinecraftClient.getInstance().getWindow().getScaledWidth() / 2 - 100;
        if (labelYPositions[0] != 0) {
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Server Name"), x, labelYPositions[0], 0xA0A0A0, false);
        }
        if (labelYPositions[1] != 0) {
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Server Address"), x, labelYPositions[1], 0xA0A0A0, false);
        }
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Download URL"), x, MinecraftClient.getInstance().getWindow().getScaledHeight() / 4 + 50, 0xA0A0A0, false);
    }

    // Redirect original label draw call for "Server Name"
    @Redirect(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
            ordinal = 0))
    private int skipNameLabel(DrawContext context, net.minecraft.client.font.TextRenderer font, Text text, int x, int y, int color) {
        return 0;
    }

    // Redirect original label draw call for "Server Address"
    @Redirect(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
            ordinal = 1))
    private int skipAddressLabel(DrawContext context, net.minecraft.client.font.TextRenderer font, Text text, int x, int y, int color) {
        return 0;
    }
}