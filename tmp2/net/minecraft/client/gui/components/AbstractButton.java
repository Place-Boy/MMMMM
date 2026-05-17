package net.minecraft.client.gui.components;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractButton extends AbstractWidget.WithInactiveMessage {
    protected static final int TEXT_MARGIN = 2;
    protected static final WidgetSprites SPRITES = new WidgetSprites(
        Identifier.withDefaultNamespace("widget/button"),
        Identifier.withDefaultNamespace("widget/button_disabled"),
        Identifier.withDefaultNamespace("widget/button_highlighted")
    );
    private @Nullable Supplier<Boolean> overrideRenderHighlightedSprite;

    public AbstractButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public abstract void onPress(InputWithModifiers input);

    @Override
    protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractContents(graphics, mouseX, mouseY, a);
        this.handleCursor(graphics);
    }

    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    protected void extractDefaultLabel(ActiveTextCollector output) {
        // Neo: Apply forced foreground color if set
        var message = this.getMessage();
        if (getFGColor() != UNSET_FG_COLOR) {
            message = message.copy().withStyle(style -> style.withColor(getFGColor()));
        }

        this.extractScrollingStringOverContents(output, message, 2);
    }

    protected final void extractDefaultSprite(GuiGraphicsExtractor graphics) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            SPRITES.get(this.active, this.overrideRenderHighlightedSprite != null ? this.overrideRenderHighlightedSprite.get() : this.isHoveredOrFocused()),
            this.getX(),
            this.getY(),
            this.getWidth(),
            this.getHeight(),
            ARGB.white(this.alpha)
        );
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.onPress(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.isActive()) {
            return false;
        } else if (event.isSelection()) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onPress(event);
            return true;
        } else {
            return false;
        }
    }

    public void setOverrideRenderHighlightedSprite(Supplier<Boolean> overrideRenderHighlightedSprite) {
        this.overrideRenderHighlightedSprite = overrideRenderHighlightedSprite;
    }
}
