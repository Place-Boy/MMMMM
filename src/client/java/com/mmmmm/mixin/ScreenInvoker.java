package com.mmmmm.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import 	net.minecraft.client.gui.narration.NarratableEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// TODO(Ravel): can not resolve target class Screen
// TODO(Ravel): can not resolve target class Screen
@Mixin(Screen.class)
public interface ScreenInvoker {
    // TODO(Ravel): Could not determine a single target
// TODO(Ravel): Could not determine a single target
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T invokeAddDrawableChild(T drawable);
}