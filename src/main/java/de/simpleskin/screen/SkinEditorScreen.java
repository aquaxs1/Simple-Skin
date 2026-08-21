package de.simpleskin.screen;

import de.simpleskin.SimpleSkinClient;
import de.simpleskin.data.StoredSkin;
import de.simpleskin.skin.SkinEditor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Edits a stored skin: turns the second-layer overlays on and off, transplants a head from
 * another skin, and swaps one colour for another.
 *
 * <p>Every edit writes a new library entry, so the original is never destroyed.
 */
public final class SkinEditorScreen extends Screen {
    private final Screen parent;
    private final StoredSkin source;
    private final SimpleSkinClient mod = SimpleSkinClient.get();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private byte[] working;
    private StoredSkin preview;
    private String status = "Edits are saved as a new skin.";
    private EditBox fromColour;
    private EditBox toColour;
    private int donorIndex;
    private List<StoredSkin> donors = List.of();

    public SkinEditorScreen(Screen parent, StoredSkin source) {
        super(Component.literal("Skin editor"));
        this.parent = parent;
        this.source = source;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(620, width - 24);
        panelHeight = Math.min(430, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        if (working == null) {
            try {
                working = Files.readAllBytes(mod.repository().imagePath(source));
            } catch (IOException exception) {
                status = "The skin PNG could not be read.";
                working = new byte[0];
            }
        }
        donors = mod.repository().savedSkins().stream().filter(skin -> skin != source).toList();

        int left = panelX + 18;
        int right = panelX + panelWidth - 18;
        int columnX = left + 150;
        int columnWidth = right - columnX;
        int y = panelY + 56;

        for (SkinEditor.Layer layer : SkinEditor.Layer.values()) {
            boolean present = hasLayer(layer);
            ThemedButton button = new ThemedButton(columnX, y, columnWidth / 2 - 4, 18,
                    Component.literal((present ? "Remove " : "Gone: ") + layer.label()),
                    ignored -> clearLayer(layer), false);
            if (!present) {
                button.disabled();
            }
            addRenderableWidget(button);
            y += 21;
        }

        int actionsX = columnX + columnWidth / 2 + 4;
        int actionsY = panelY + 56;
        String donorName = donors.isEmpty() ? "no other skin" : donors.get(donorIndex % donors.size()).name();
        addRenderableWidget(new ThemedButton(actionsX, actionsY, columnWidth / 2 - 4, 18,
                Component.literal("Head: " + donorName), ignored -> {
                    if (!donors.isEmpty()) {
                        donorIndex = (donorIndex + 1) % donors.size();
                        rebuildWidgets();
                    }
                }, false).tooltip(Component.literal("Choose which skin's head to graft on")));
        ThemedButton swap = new ThemedButton(actionsX, actionsY + 21, columnWidth / 2 - 4, 18,
                Component.literal("Swap head"), ignored -> swapHead(), false);
        if (donors.isEmpty()) {
            swap.disabled();
        }
        addRenderableWidget(swap);

        fromColour = new EditBox(font, actionsX, actionsY + 63, columnWidth / 2 - 4, 18,
                Component.literal("From colour"));
        fromColour.setMaxLength(7);
        fromColour.setValue(fromColour.getValue().isEmpty() ? "#000000" : fromColour.getValue());
        addRenderableWidget(fromColour);
        toColour = new EditBox(font, actionsX, actionsY + 84, columnWidth / 2 - 4, 18,
                Component.literal("To colour"));
        toColour.setMaxLength(7);
        toColour.setValue(toColour.getValue().isEmpty() ? "#ffffff" : toColour.getValue());
        addRenderableWidget(toColour);
        addRenderableWidget(new ThemedButton(actionsX, actionsY + 105, columnWidth / 2 - 4, 18,
                Component.literal("Replace colour"), ignored -> replaceColour(), false)
                .tooltip(Component.literal("Replace every pixel of the first colour with the second")));

        addRenderableWidget(new ThemedButton(columnX, panelY + panelHeight - 34, 110, 20,
                Component.literal("Save as new"), ignored -> saveAsNew(), true));
        addRenderableWidget(new ThemedButton(columnX + 116, panelY + panelHeight - 34, 80, 20,
                Component.literal("Reset"), ignored -> reset(), false));
        addRenderableWidget(new ThemedButton(panelX + 18, panelY + panelHeight - 34, 72, 20,
                Component.literal("Back"), ignored -> onClose(), false));
    }

    private boolean hasLayer(SkinEditor.Layer layer) {
        try {
            return working.length > 0 && SkinEditor.hasLayer(working, layer);
        } catch (IOException exception) {
            return false;
        }
    }

    private void clearLayer(SkinEditor.Layer layer) {
        apply(() -> SkinEditor.clearLayer(working, layer), layer.label() + " removed.");
    }

    private void swapHead() {
        if (donors.isEmpty()) {
            return;
        }
        StoredSkin donor = donors.get(donorIndex % donors.size());
        apply(() -> SkinEditor.swapHead(working, Files.readAllBytes(mod.repository().imagePath(donor))),
                "Head taken from " + donor.name() + ".");
    }

    private void replaceColour() {
        Integer from = parseColour(fromColour.getValue());
        Integer to = parseColour(toColour.getValue());
        if (from == null || to == null) {
            status = "Colours must look like #rrggbb.";
            return;
        }
        apply(() -> SkinEditor.replaceColour(working, from, to, 0), "Colour replaced.");
    }

    /** Runs one edit against the working image and refreshes the preview. */
    private void apply(Edit edit, String message) {
        try {
            working = edit.run();
            status = message;
            refreshPreview();
        } catch (IOException exception) {
            status = "That edit failed: " + exception.getMessage();
        }
        rebuildWidgets();
    }

    /**
     * Stores the working image under a temporary library entry purely so the preview renderer has
     * a texture to draw. The entry is dropped again when the screen closes without saving.
     */
    private void refreshPreview() {
        discardPreview();
        try {
            preview = mod.repository().add(working, source.name() + " (edit)", source.sourcePlayer(),
                    source.model(), false);
        } catch (IOException exception) {
            status = "The edited skin could not be previewed: " + exception.getMessage();
        }
    }

    private void discardPreview() {
        if (preview != null) {
            mod.textures().invalidate(preview);
            mod.repository().delete(preview);
            preview = null;
        }
    }

    private void saveAsNew() {
        try {
            // The preview entry becomes the real one instead of writing the bytes twice.
            StoredSkin saved = preview;
            preview = null;
            if (saved == null) {
                saved = mod.repository().add(working, source.name() + " (edit)", source.sourcePlayer(),
                        source.model(), true);
            } else {
                saved.setSaved(true);
                mod.repository().update(saved);
            }
            status = "Saved " + saved.name() + ".";
            minecraft.gui.setScreen(new SkinSettingsScreen(parent, saved));
        } catch (IOException exception) {
            status = "Saving failed: " + exception.getMessage();
        }
    }

    private void reset() {
        discardPreview();
        try {
            working = Files.readAllBytes(mod.repository().imagePath(source));
            status = "Back to the original.";
        } catch (IOException exception) {
            status = "The original could not be re-read.";
        }
        rebuildWidgets();
    }

    static Integer parseColour(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, SimpleSkinTheme.BACKDROP);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SimpleSkinTheme.PANEL_EDGE);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, SimpleSkinTheme.PANEL);
        graphics.text(font, Component.literal("SKIN EDITOR"), panelX + 18, panelY + 17, SimpleSkinTheme.PAPER, true);
        graphics.text(font, font.plainSubstrByWidth(source.name(), panelWidth - 150),
                panelX + 118, panelY + 17, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.horizontalLine(panelX + 18, panelX + panelWidth - 18, panelY + 36, SimpleSkinTheme.PANEL_EDGE);

        int previewLeft = panelX + 18;
        graphics.fill(previewLeft, panelY + 52, previewLeft + 130, panelY + panelHeight - 44, 0xFF0F1317);
        StoredSkin shown = preview != null ? preview : source;
        try {
            Identifier texture = mod.textures().getOrLoad(shown);
            SkinPreviewRenderer.draw(graphics, texture, shown.model(), previewLeft + 65, panelY + 74, 4);
        } catch (IOException ignored) {
            graphics.centeredText(font, "no preview", previewLeft + 65, panelY + 120, SimpleSkinTheme.PAPER_MUTED);
        }

        graphics.text(font, "LAYERS", panelX + 168, panelY + 44, SimpleSkinTheme.PAPER_MUTED, false);
        graphics.text(font, font.plainSubstrByWidth(status, panelWidth - 36),
                panelX + 18, panelY + panelHeight - 58, SimpleSkinTheme.PAPER_MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        // An unsaved preview entry must not linger in the library.
        discardPreview();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One image edit, allowed to fail with an {@link IOException}. */
    @FunctionalInterface
    private interface Edit {
        byte[] run() throws IOException;
    }
}
