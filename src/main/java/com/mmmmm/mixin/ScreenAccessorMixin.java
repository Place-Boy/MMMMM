package com.mmmmm.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessorMixin {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T invokeAddRenderableWidget(T widget);
}

