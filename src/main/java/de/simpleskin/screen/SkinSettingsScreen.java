package de.simpleskin.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import de.simpleskin.SimpleSkinClient;
import de.simpleskin.data.CapeChoice;
import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.SkinModel;
import de.simpleskin.data.StoredSkin;
import de.simpleskin.skin.MinecraftSkinUploadService;
import de.simpleskin.skin.SkinOverrideManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
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
    private EditBox nameField;
    private ThemedButton keyButton;
    private ThemedButton saveButton;
    private ThemedButton deleteButton;
    private RemotePlayer previewPlayer;
    private java.util.List<MinecraftSkinUploadService.ProfileCape> ownedCapes = java.util.List.of();
    private boolean capesRequested;

    public SkinSettingsScreen(Screen parent, StoredSkin skin) {
        super(Component.literal("Skin settings"));
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
            if (minecraft.level != null && previewPlayer == null) {
                previewPlayer = new RemotePlayer(minecraft.level, new GameProfile(previewId, skin.name()));
            }
        } catch (IOException exception) {
            status = "The preview PNG could not be loaded.";
        }

        int x = split + 18;
        int fieldWidth = panelX + panelWidth - 18 - x;
        nameField = new EditBox(font, x, panelY + 66, fieldWidth, 20, Component.literal("Skin name"));
        nameField.setMaxLength(40);
        nameField.setValue(skin.name());
        addRenderableWidget(nameField);

        int half = (fieldWidth - 6) / 2;
        addRenderableWidget(new ThemedButton(x, panelY + 112, half, 20, Component.literal("Wide arms"), ignored -> {
            skin.setModel(SkinModel.WIDE);
            refreshPreview();
        }, skin.model() == SkinModel.WIDE));
        addRenderableWidget(new ThemedButton(x + half + 6, panelY + 112, half, 20, Component.literal("Slim arms"), ignored -> {
            skin.setModel(SkinModel.SLIM);
            refreshPreview();
        }, skin.model() == SkinModel.SLIM));

        keyButton = new ThemedButton(x, panelY + 158, fieldWidth, 20, keyLabel(), ignored -> {
            listeningForKey = true;
            keyButton.setMessage(Component.literal("Press a key, Esc to cancel..."));
        }, false);
        addRenderableWidget(keyButton);

        SimpleSkinConfig.Visibility visibility = mod.config().visibility();
        addRenderableWidget(new ThemedButton(x, panelY + 222, fieldWidth, 20,
                Component.literal("Visibility: " + visibility.label()), ignored -> {
                    mod.config().setVisibility(mod.config().visibility().next());
                    rebuildWidgets();
                }, false).tooltip(Component.literal(visibility.description())));

        addRenderableWidget(new ThemedButton(x, panelY + 190, half, 20,
                Component.literal(skin.capeChoice().label()), ignored -> {
                    skin.setCapeChoice(skin.capeChoice().next());
                    persist();
                    if (skin.capeChoice() == CapeChoice.SPECIFIC) {
                        loadCapes();
                    }
                    rebuildWidgets();
                }, false).tooltip(Component.literal(
                        "Mojang only allows capes your account already owns; none can be uploaded")));
        ThemedButton capePick = new ThemedButton(x + half + 6, panelY + 190, half, 20,
                Component.literal(capeLabel()), ignored -> cycleCape(), false);
        if (skin.capeChoice() != CapeChoice.SPECIFIC || ownedCapes.isEmpty()) {
            capePick.disabled();
        }
        addRenderableWidget(capePick);

        addRenderableWidget(new ThemedButton(x, panelY + 254, half, 20, Component.literal("Export PNG"),
                ignored -> export(), false));
        addRenderableWidget(new ThemedButton(x, panelY + 306, fieldWidth, 20, Component.literal("Edit skin"),
                ignored -> minecraft.gui.setScreen(new SkinEditorScreen(this, skin)), false)
                .tooltip(Component.literal("Layers, head swap and colour replace")));
        saveButton = new ThemedButton(x + half + 6, panelY + 254, half, 20,
                Component.literal(skin.saved() ? "Saved" : "Save"), ignored -> save(), false);
        if (skin.saved()) {
            saveButton.disabled();
        }
        addRenderableWidget(saveButton);

        deleteButton = new ThemedButton(x, panelY + 280, fieldWidth, 20,
                Component.literal(confirmingDelete ? "Click again to delete" : "Delete skin"),
                ignored -> delete(), ThemedButton.Style.DANGER);
        addRenderableWidget(deleteButton);

        addRenderableWidget(new ThemedButton(x, panelY + panelHeight - 76, fieldWidth, 24,
                Component.literal("Equip skin"), ignored -> equip(), true));
        addRenderableWidget(new ThemedButton(panelX + 18, panelY + panelHeight - 32, 72, 20,
                Component.literal("Back"), ignored -> onClose(), false));
        if (mod.visibility().rejoinPending()) {
            addRenderableWidget(new ThemedButton(panelX + 96, panelY + panelHeight - 32, 84, 20,
                    Component.literal("Rejoin now"), ignored -> mod.visibility().rejoinNow(message -> status = message), true)
                    .tooltip(Component.literal("Reconnect so other players load your new skin")));
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
        rebuildWidgets();
    }

    /** Fetches the capes this account owns, once, so the picker has something to cycle through. */
    private void loadCapes() {
        if (capesRequested) {
            return;
        }
        capesRequested = true;
        mod.uploads().fetchCapes().thenAccept(capes -> minecraft.execute(() -> {
            ownedCapes = capes;
            if (capes.isEmpty()) {
                status = "This account owns no capes.";
            }
            rebuildWidgets();
        }));
    }

    private String capeLabel() {
        if (skin.capeChoice() != CapeChoice.SPECIFIC) {
            return "—";
        }
        if (ownedCapes.isEmpty()) {
            return capesRequested ? "no capes" : "load capes";
        }
        return ownedCapes.stream()
                .filter(cape -> cape.id().equals(skin.capeId()))
                .findFirst()
                .map(MinecraftSkinUploadService.ProfileCape::alias)
                .orElse(ownedCapes.get(0).alias());
    }

    /** Steps to the next owned cape and pre-loads its texture so the preview updates. */
    private void cycleCape() {
        if (ownedCapes.isEmpty()) {
            loadCapes();
            return;
        }
        int current = 0;
        for (int index = 0; index < ownedCapes.size(); index++) {
            if (ownedCapes.get(index).id().equals(skin.capeId())) {
                current = index + 1;
                break;
            }
        }
        MinecraftSkinUploadService.ProfileCape chosen = ownedCapes.get(current % ownedCapes.size());
        skin.setCapeId(chosen.id());
        persist();
        mod.capes().load(chosen.id(), chosen.url()).thenAccept(ignored -> minecraft.execute(this::rebuildWidgets));
        status = "Cape set to " + chosen.alias() + ".";
        rebuildWidgets();
    }

    private Component keyLabel() {
        if (skin.keyCode() < 0) {
            return Component.literal("Skin hotkey: Not set");
        }
        Component key = InputConstants.Type.KEYSYM.getOrCreate(skin.keyCode()).getDisplayName();
        return Component.literal("Skin hotkey: ").append(key);
    }

    private void save() {
        skin.setSaved(true);
        persist();
        status = "Saved to Change Skin.";
        rebuildWidgets();
    }

    /** Two-step delete: the first click arms it, the second removes the skin for good. */
    private void delete() {
        if (!confirmingDelete) {
            confirmingDelete = true;
            deleteButton.setMessage(Component.literal("Click again to delete"));
            status = "Click Delete again to remove " + skin.name() + " permanently.";
            return;
        }
        if (mod.equippedSkin() == skin) {
            mod.unequip(message -> status = message);
        }
        mod.textures().invalidate(skin);
        mod.repository().delete(skin);
        minecraft.gui.setScreen(parent);
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
            skin.setName(nameField.getValue());
        }
        mod.repository().update(skin);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, SimpleSkinTheme.BACKDROP);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SimpleSkinTheme.PANEL_EDGE);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, SimpleSkinTheme.PANEL);
        graphics.text(font, Component.literal("SKIN SETTINGS"), panelX + 18, panelY + 17, SimpleSkinTheme.PAPER, true);
        String heading = nameField == null ? skin.name() : nameField.getValue();
        graphics.text(font, font.plainSubstrByWidth(heading, panelWidth - 150),
                panelX + 126, panelY + 17, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.horizontalLine(panelX + 18, panelX + panelWidth - 18, panelY + 36, SimpleSkinTheme.PANEL_EDGE);

        graphics.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF0F1317);
        graphics.outline(previewLeft, previewTop, previewRight - previewLeft,
                previewBottom - previewTop, SimpleSkinTheme.PANEL_EDGE);
        int centerX = (previewLeft + previewRight) / 2;
        int floorY = previewBottom - 22;
        graphics.fill(centerX - 58, floorY, centerX + 58, floorY + 2, 0xFF343C43);
        renderPlayer(graphics);
        graphics.centeredText(font, "DRAG TO ROTATE", centerX, previewBottom - 13, SimpleSkinTheme.PAPER_MUTED);

        int labelX = previewRight + 30;
        graphics.text(font, "NAME", labelX, panelY + 53, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.text(font, "MODEL", labelX, panelY + 99, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.text(font, "QUICK EQUIP", labelX, panelY + 145, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.text(font, "WHO SEES IT", labelX, panelY + 212, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.text(font, "CAPE", labelX, panelY + 180, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.text(font, "ACTIONS", labelX, panelY + 242, SimpleSkinTheme.PAPER_MUTED, false);

        graphics.text(font, font.plainSubstrByWidth(status, panelWidth - 230),
                panelX + 190, panelY + panelHeight - 27, SimpleSkinTheme.PAPER_MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderPlayer(GuiGraphicsExtractor graphics) {
        if (previewPlayer == null) {
            return;
        }
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher()
                .extractEntity(previewPlayer, 1.0f);
        state.lightCoords = 15728880;
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f + rotation;
            living.yRot = 0.0f;
            living.xRot = pitch;
        }
        Quaternionf orientation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        Vector3f offset = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + 0.08f, 0.0f);
        graphics.entity(state, Math.min(82.0f, (previewBottom - previewTop) / 3.2f), offset, orientation, tilt,
                previewLeft, previewTop, previewRight, previewBottom - 16);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Any click that is not the delete button disarms the delete confirmation.
        if (confirmingDelete && !isOver(deleteButton, event.x(), event.y())) {
            confirmingDelete = false;
            deleteButton.setMessage(Component.literal("Delete skin"));
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.x() >= previewLeft && event.x() <= previewRight
                && event.y() >= previewTop && event.y() <= previewBottom) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean isOver(ThemedButton button, double x, double y) {
        return button != null && x >= button.getX() && x < button.getRight()
                && y >= button.getY() && y < button.getBottom();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            rotation = (rotation + (float) deltaX * 2.2f) % 360.0f;
            pitch = Math.max(-25.0f, Math.min(25.0f, pitch - (float) deltaY * 0.7f));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningForKey) {
            captureHotkey(event.key());
            return true;
        }
        return super.keyPressed(event);
    }

    private void captureHotkey(int key) {
        listeningForKey = false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            status = "Hotkey unchanged.";
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            mod.repository().assignKeyCode(skin, -1);
            status = "Hotkey cleared.";
        } else if (key != GLFW.GLFW_KEY_UNKNOWN) {
            Optional<KeyMapping> conflict = SimpleSkinClient.conflictingKeyBinding(key);
            Optional<StoredSkin> takenBy = mod.repository().findByKeyCode(key, skin);
            mod.repository().assignKeyCode(skin, key);
            String keyName = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
            if (conflict.isPresent()) {
                status = keyName + " is also bound to "
                        + Component.translatable(conflict.get().getName()).getString() + ".";
            } else if (takenBy.isPresent()) {
                status = keyName + " was taken from " + takenBy.get().name() + ".";
            } else {
                status = "Hotkey set to " + keyName + ".";
            }
        }
        keyButton.setMessage(keyLabel());
    }

    @Override
    public void onClose() {
        persist();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        SkinOverrideManager.clear(previewId);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
