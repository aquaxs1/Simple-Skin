package de.simpleskin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class ThemedButton extends ClickableWidget {
    private final Consumer<ThemedButton> action;
    private final Style style;

    public ThemedButton(int x, int y, int width, int height, Text message,
            Consumer<ThemedButton> action, boolean accent) {
        this(x, y, width, height, message, action, accent ? Style.ACCENT : Style.NORMAL);
    }

    public ThemedButton(int x, int y, int width, int height, Text message,
            Consumer<ThemedButton> action, Style style) {
        super(x, y, width, height, message);
        this.action = action;
        this.style = style;
    }

    public ThemedButton tooltip(Text tooltip) {
        setTooltip(Tooltip.of(tooltip));
        return this;
    }

    public ThemedButton disabled() {
        this.active = false;
        return this;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
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
        } else if (isHovered() || isFocused()) {
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
        context.fill(getX(), getY(), getRight(), getBottom(), edge);
        context.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, fill);

        boolean lit = active && (isHovered() || isFocused()) && style != Style.NORMAL;
        int color = !active ? 0xFF777A7D : (lit ? SimpleSkinTheme.INK : SimpleSkinTheme.PAPER);
        Text label = trimToWidth(getMessage(), getWidth() - 8);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
    }

    /** Keeps long labels (skin names, hotkey names) inside the button instead of bleeding out. */
    private static Text trimToWidth(Text message, int maxWidth) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        String raw = message.getString();
        if (maxWidth <= 0 || renderer.getWidth(raw) <= maxWidth) {
            return message;
        }
        return Text.literal(renderer.trimToWidth(raw, Math.max(0, maxWidth - renderer.getWidth("..."))) + "...");
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        press();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_SPACE) {
            press();
            return true;
        }
        return false;
    }

    private void press() {
        if (active) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            action.accept(this);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    public enum Style {
        NORMAL,
        ACCENT,
        DANGER
    }
}
