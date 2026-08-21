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
    private final boolean accent;

    public ThemedButton(int x, int y, int width, int height, Text message,
            Consumer<ThemedButton> action, boolean accent) {
        super(x, y, width, height, message);
        this.action = action;
        this.accent = accent;
    }

    public ThemedButton tooltip(Text tooltip) {
        setTooltip(Tooltip.of(tooltip));
        return this;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int fill = accent ? SimpleSkinTheme.COPPER_DARK : SimpleSkinTheme.SURFACE;
        int edge = accent ? SimpleSkinTheme.COPPER : SimpleSkinTheme.PANEL_EDGE;
        if (!active) {
            fill = 0xFF1B1F23;
            edge = 0xFF34383C;
        } else if (isHovered()) {
            fill = accent ? SimpleSkinTheme.COPPER : SimpleSkinTheme.SURFACE_HOVER;
            edge = accent ? 0xFFFFB17D : SimpleSkinTheme.PAPER_MUTED;
        }
        context.fill(getX(), getY(), getRight(), getBottom(), edge);
        context.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, fill);
        int color = active ? (accent && isHovered() ? SimpleSkinTheme.INK : SimpleSkinTheme.PAPER) : 0xFF777A7D;
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
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
}
