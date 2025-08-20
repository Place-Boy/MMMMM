package com.mmmmm.mixin;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenMixin {
    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T invokeAddDrawableChild(T drawableElement);

    @Invoker("children")
    java.util.List<Element> invokeChildren();

    @Accessor("drawables")
    java.util.List<Drawable> invokeDrawables();

    @Accessor("selectables")
    java.util.List<Selectable> invokeSelectables();
}