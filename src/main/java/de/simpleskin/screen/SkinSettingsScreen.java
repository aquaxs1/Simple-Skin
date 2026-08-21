package de.simpleskin.screen;

import com.mojang.authlib.GameProfile;
import de.simpleskin.SimpleSkinClient;
import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.SkinModel;
import de.simpleskin.data.StoredSkin;
import de.simpleskin.skin.SkinOverrideManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class SkinSettingsScreen extends Screen {
    private final Screen parent;
    private final StoredSkin skin;
    private final SimpleSkinClient mod = SimpleSkinClient.get();
    private final UUID previewId;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int previewLeft;
    private int previewRight;
    private int previewTop;
    private int previewBottom;
    private float rotation;
    private float pitch;
    private boolean dragging;
    private boolean listeningForKey;
    private boolean confirmingDelete;
    private String status = "Drag the preview to rotate.";
    private TextFieldWidget nameField;
    private ThemedButton keyButton;
    private ThemedButton saveButton;
    private ThemedButton deleteButton;
    private OtherClientPlayerEntity previewPlayer;

    public SkinSettingsScreen(Screen parent, StoredSkin skin) {
        super(Text.literal("Skin settings"));
        this.parent = parent;
        this.skin = skin;
        this.previewId = UUID.nameUUIDFromBytes(("simple-skin-preview:" + skin.id()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(690, width - 24);
        panelHeight = Math.min(460, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int split = panelX + Math.min(300, panelWidth * 44 / 100);
        previewLeft = panelX + 18;
        previewRight = split - 12;
        previewTop = panelY + 52;
        previewBottom = panelY + panelHeight - 44;

        try {
            SkinOverrideManager.set(previewId, mod.textures().getOrLoad(skin), skin.model());
            if (client.world != null && previewPlayer == null) {
                previewPlayer = new OtherClientPlayerEntity(client.world, new GameProfile(previewId, skin.name()));
            }
        } catch (IOException exception) {
            status = "The preview PNG could not be loaded.";
        }

        int x = split + 18;
        int fieldWidth = panelX + panelWidth - 18 - x;
        nameField = new TextFieldWidget(textRenderer, x, panelY + 66, fieldWidth, 20, Text.literal("Skin name"));
        nameField.setMaxLength(40);
        nameField.setText(skin.name());
        addDrawableChild(nameField);

        int half = (fieldWidth - 6) / 2;
        addDrawableChild(new ThemedButton(x, panelY + 112, half, 20, Text.literal("Wide arms"), ignored -> {
            skin.setModel(SkinModel.WIDE);
            refreshPreview();
        }, skin.model() == SkinModel.WIDE));
        addDrawableChild(new ThemedButton(x + half + 6, panelY + 112, half, 20, Text.literal("Slim arms"), ignored -> {
            skin.setModel(SkinModel.SLIM);
            refreshPreview();
        }, skin.model() == SkinModel.SLIM));

        keyButton = new ThemedButton(x, panelY + 158, fieldWidth, 20, keyLabel(), ignored -> {
            listeningForKey = true;
            keyButton.setMessage(Text.literal("Press a key, Esc to cancel..."));
        }, false);
        addDrawableChild(keyButton);

        SimpleSkinConfig.Visibility visibility = mod.config().visibility();
        addDrawableChild(new ThemedButton(x, panelY + 204, fieldWidth, 20,
                Text.literal("Visibility: " + visibility.label()), ignored -> {
                    mod.config().setVisibility(mod.config().visibility().next());
                    clearAndInit();
                }, false).tooltip(Text.literal(visibility.description())));

        addDrawableChild(new ThemedButton(x, panelY + 236, half, 20, Text.literal("Export PNG"), ignored -> export(), false));
        saveButton = new ThemedButton(x + half + 6, panelY + 236, half, 20,
                Text.literal(skin.saved() ? "Saved" : "Save"), ignored -> save(), false);
        if (skin.saved()) {
            saveButton.disabled();
        }
        addDrawableChild(saveButton);

        deleteButton = new ThemedButton(x, panelY + 262, fieldWidth, 20,
                Text.literal(confirmingDelete ? "Click again to delete" : "Delete skin"),
                ignored -> delete(), ThemedButton.Style.DANGER);
        addDrawableChild(deleteButton);

        addDrawableChild(new ThemedButton(x, panelY + panelHeight - 76, fieldWidth, 24,
                Text.literal("Equip skin"), ignored -> equip(), true));
        addDrawableChild(new ThemedButton(panelX + 18, panelY + panelHeight - 32, 72, 20,
                Text.literal("Back"), ignored -> close(), false));
        if (mod.visibility().rejoinPending()) {
            addDrawableChild(new ThemedButton(panelX + 96, panelY + panelHeight - 32, 84, 20,
                    Text.literal("Rejoin now"), ignored -> mod.visibility().rejoinNow(message -> status = message), true)
                    .tooltip(Text.literal("Reconnect so other players load your new skin")));
        }
    }

    private void refreshPreview() {
        persist();
        try {
            // Re-applies to the player too, but only if this skin is the one being worn.
            mod.refresh(skin);
            SkinOverrideManager.set(previewId, mod.textures().getOrLoad(skin), skin.model());
        } catch (IOException ignored) {
            status = "The preview PNG could not be loaded.";
        }
        clearAndInit();
    }

    private Text keyLabel() {
        if (skin.keyCode() < 0) {
            return Text.literal("Skin hotkey: Not set");
        }
        Text key = InputUtil.Type.KEYSYM.createFromCode(skin.keyCode()).getLocalizedText();
        return Text.literal("Skin hotkey: ").append(key);
    }

    private void save() {
        skin.setSaved(true);
        persist();
        status = "Saved to Change Skin.";
        clearAndInit();
    }

    /** Two-step delete: the first click arms it, the second removes the skin for good. */
    private void delete() {
        if (!confirmingDelete) {
            confirmingDelete = true;
            deleteButton.setMessage(Text.literal("Click again to delete"));
            status = "Click Delete again to remove " + skin.name() + " permanently.";
            return;
        }
        if (mod.equippedSkin() == skin) {
            mod.unequip(message -> status = message);
        }
        mod.textures().invalidate(skin);
        mod.repository().delete(skin);
        client.setScreen(parent);
    }

    private void equip() {
        persist();
        mod.equip(skin, message -> status = message);
    }

    private void export() {
        persist();
        try {
            Path path = mod.repository().export(skin);
            status = "Exported to " + path.getFileName() + ".";
        } catch (IOException exception) {
            status = "The skin could not be exported.";
        }
    }

    private void persist() {
        if (nameField != null) {
            skin.setName(nameField.getText());
        }
        mod.repository().update(skin);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SimpleSkinTheme.BACKDROP);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SimpleSkinTheme.PANEL_EDGE);
        context.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, SimpleSkinTheme.PANEL);
        context.drawTextWithShadow(textRenderer, Text.literal("SKIN SETTINGS"), panelX + 18, panelY + 17, SimpleSkinTheme.PAPER);
        String heading = nameField == null ? skin.name() : nameField.getText();
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(heading, panelWidth - 150)),
                panelX + 126, panelY + 17, SimpleSkinTheme.PAPER_MUTED, false);
        context.drawHorizontalLine(panelX + 18, panelX + panelWidth - 18, panelY + 36, SimpleSkinTheme.PANEL_EDGE);

        context.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF0F1317);
        context.drawStrokedRectangle(previewLeft, previewTop, previewRight - previewLeft,
                previewBottom - previewTop, SimpleSkinTheme.PANEL_EDGE);
        int centerX = (previewLeft + previewRight) / 2;
        int floorY = previewBottom - 22;
        context.fill(centerX - 58, floorY, centerX + 58, floorY + 2, 0xFF343C43);
        renderPlayer(context);
        context.drawCenteredTextWithShadow(textRenderer, "DRAG TO ROTATE", centerX, previewBottom - 13, SimpleSkinTheme.PAPER_MUTED);

        int labelX = previewRight + 30;
        context.drawText(textRenderer, "NAME", labelX, panelY + 53, SimpleSkinTheme.PAPER_MUTED, false);
        context.drawText(textRenderer, "MODEL", labelX, panelY + 99, SimpleSkinTheme.PAPER_MUTED, false);
        context.drawText(textRenderer, "QUICK EQUIP", labelX, panelY + 145, SimpleSkinTheme.PAPER_MUTED, false);
        context.drawText(textRenderer, "WHO SEES IT", labelX, panelY + 191, SimpleSkinTheme.PAPER_MUTED, false);
        context.drawText(textRenderer, "ACTIONS", labelX, panelY + 223, SimpleSkinTheme.PAPER_MUTED, false);

        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(status, panelWidth - 230)),
                panelX + 190, panelY + panelHeight - 27, SimpleSkinTheme.PAPER_MUTED, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayer(DrawContext context) {
        if (previewPlayer == null) {
            return;
        }
        EntityRenderState state = MinecraftClient.getInstance().getEntityRenderDispatcher()
                .getAndUpdateRenderState(previewPlayer, 1.0f);
        state.light = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyYaw = 180.0f + rotation;
            living.relativeHeadYaw = 0.0f;
            living.pitch = pitch;
            living.width /= living.baseScale;
            living.height /= living.baseScale;
            living.baseScale = 1.0f;
        }
        Quaternionf orientation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        Vector3f offset = new Vector3f(0.0f, state.height / 2.0f + 0.08f, 0.0f);
        context.addEntity(state, Math.min(82, (previewBottom - previewTop) / 3.2f), offset, orientation, tilt,
                previewLeft, previewTop, previewRight, previewBottom - 16);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Any click that is not the delete button disarms the delete confirmation.
        if (confirmingDelete && !isOver(deleteButton, click.x(), click.y())) {
            confirmingDelete = false;
            deleteButton.setMessage(Text.literal("Delete skin"));
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.x() >= previewLeft && click.x() <= previewRight
                && click.y() >= previewTop && click.y() <= previewBottom) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private static boolean isOver(ThemedButton button, double x, double y) {
        return button != null && x >= button.getX() && x < button.getRight()
                && y >= button.getY() && y < button.getBottom();
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            rotation = (rotation + (float) deltaX * 2.2f) % 360.0f;
            pitch = Math.max(-25.0f, Math.min(25.0f, pitch - (float) deltaY * 0.7f));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listeningForKey) {
            captureHotkey(input.getKeycode());
            return true;
        }
        return super.keyPressed(input);
    }

    private void captureHotkey(int key) {
        listeningForKey = false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            status = "Hotkey unchanged.";
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            mod.repository().assignKeyCode(skin, -1);
            status = "Hotkey cleared.";
        } else if (key != GLFW.GLFW_KEY_UNKNOWN) {
            Optional<KeyBinding> conflict = SimpleSkinClient.conflictingKeyBinding(key);
            Optional<StoredSkin> takenBy = mod.repository().findByKeyCode(key, skin);
            mod.repository().assignKeyCode(skin, key);
            String keyName = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
            if (conflict.isPresent()) {
                status = keyName + " is also bound to "
                        + KeyBinding.getLocalizedName(conflict.get().getId()).get().getString() + ".";
            } else if (takenBy.isPresent()) {
                status = keyName + " was taken from " + takenBy.get().name() + ".";
            } else {
                status = "Hotkey set to " + keyName + ".";
            }
        }
        keyButton.setMessage(keyLabel());
    }

    @Override
    public void close() {
        persist();
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        SkinOverrideManager.clear(previewId);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
