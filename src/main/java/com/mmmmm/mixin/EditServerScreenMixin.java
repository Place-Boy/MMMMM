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
                .forEach(c -> {
                    if (c instanceof EditBox editBox) {
                        LOGGER.info("Child: " + editBox.getClass().getName() + " at Y: " + editBox.getY());
                    } else if (c instanceof net.minecraft.client.gui.components.CycleButton cycleButton) {
                        LOGGER.info("Child: " + cycleButton.getClass().getName() + " at Y: " + cycleButton.getY());
                    } else if (c instanceof net.minecraft.client.gui.components.Button button) {
                        LOGGER.info("Child: " + button.getClass().getName() + " at Y: " + button.getY());
                    } else {
                        LOGGER.info("Child: " + c.getClass().getName());
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

    @Inject(method = "render", at = @At("TAIL"))
    private void render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, "Custom Field:", 10, 20, 0xFFFFFF, true); // Render text directly
        LOGGER.info("Render mixin called");
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
}