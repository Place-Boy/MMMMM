package com.mmmmm.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Screen.class)
public abstract class ScreenAccessorMixin {

    @Shadow @Final
    public List<Renderable> renderables;

    @Shadow @Final
    public List<GuiEventListener> children;

    @Shadow @Final
    public List<NarratableEntry> narratables;

    public void addRenderableWidget(Renderable widget) {
        this.renderables.add(widget);
        this.children.add((GuiEventListener) widget);
        this.narratables.add((NarratableEntry) widget);
    }
}
