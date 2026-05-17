package net.minecraft.client.gui.components.events;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.TabOrderedElement;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface GuiEventListener extends TabOrderedElement {
    default void mouseMoved(double x, double y) {
    }

    default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    default boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    default boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return false;
    }

    default boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default boolean keyReleased(KeyEvent event) {
        return false;
    }

    default boolean charTyped(CharacterEvent event) {
        return false;
    }

    default boolean preeditUpdated(@Nullable PreeditEvent event) {
        return false;
    }

    default @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
        return null;
    }

    default boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    void setFocused(final boolean focused);

    boolean isFocused();

    default boolean shouldTakeFocusAfterInteraction() {
        return true;
    }

    default @Nullable ComponentPath getCurrentFocusPath() {
        return this.isFocused() ? ComponentPath.leaf(this) : null;
    }

    default ScreenRectangle getRectangle() {
        return ScreenRectangle.empty();
    }

    default ScreenRectangle getBorderForArrowNavigation(ScreenDirection opposite) {
        return this.getRectangle().getBorder(opposite);
    }
}
