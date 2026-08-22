package de.simpleskin.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class ThemedButton extends AbstractWidget {
    private final Consumer<ThemedButton> action;
    private final Style style;

    public ThemedButton(int x, int y, int width, int height, Component message,
            Consumer<ThemedButton> action, boolean accent) {
        this(x, y, width, height, message, action, accent ? Style.ACCENT : Style.NORMAL);
    }

    public ThemedButton(int x, int y, int width, int height, Component message,
            Consumer<ThemedButton> action, Style style) {
        super(x, y, width, height, message);
        this.action = action;
        this.style = style;
    }

    public ThemedButton tooltip(Component tooltip) {
        setTooltip(Tooltip.create(tooltip));
        return this;
    }

    public ThemedButton disabled() {
        this.active = false;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int fill = switch (style) {
            case ACCENT -> SimpleSkinTheme.COPPER_DARK;
            case DANGER -> SimpleSkinTheme.DANGER_DARK;
            case NORMAL -> SimpleSkinTheme.SURFACE;
        };
        int edge = switch (style) {
            case ACCENT -> SimpleSkinTheme.COPPER;
            case DANGER -> SimpleSkinTheme.DANGER;
            case NORMAL -> SimpleSkinTheme.PANEL_EDGE;
        };
        if (!active) {
            fill = 0xFF1B1F23;
            edge = 0xFF34383C;
        } else if (isHoveredOrFocused()) {
            fill = switch (style) {
                case ACCENT -> SimpleSkinTheme.COPPER;
                case DANGER -> SimpleSkinTheme.DANGER;
                case NORMAL -> SimpleSkinTheme.SURFACE_HOVER;
            };
            edge = switch (style) {
                case ACCENT -> 0xFFFFB17D;
                case DANGER -> 0xFFFFA9A3;
                case NORMAL -> SimpleSkinTheme.PAPER_MUTED;
            };
        }
        graphics.fill(getX(), getY(), getRight(), getBottom(), edge);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, fill);

        boolean lit = active && isHoveredOrFocused() && style != Style.NORMAL;
        int color = !active ? 0xFF777A7D : (lit ? SimpleSkinTheme.INK : SimpleSkinTheme.PAPER);
        Font font = Minecraft.getInstance().font;
        graphics.centeredText(font, trimToWidth(font, getMessage(), getWidth() - 8),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
    }

    /** Keeps long labels (skin names, hotkey names) inside the button instead of bleeding out. */
    private static Component trimToWidth(Font font, Component message, int maxWidth) {
        String raw = message.getString();
        if (maxWidth <= 0 || font.width(raw) <= maxWidth) {
            return message;
        }
        return Component.literal(font.plainSubstrByWidth(raw, Math.max(0, maxWidth - font.width("..."))) + "...");
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE) {
            press();
            return true;
        }
        return false;
    }

    private void press() {
        if (active) {
            playButtonClickSound(Minecraft.getInstance().getSoundManager());
            action.accept(this);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public enum Style {
        NORMAL,
        ACCENT,
        DANGER
    }
}
