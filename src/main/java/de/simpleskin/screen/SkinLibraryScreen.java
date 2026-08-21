package de.simpleskin.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.simpleskin.SimpleSkinClient;
import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.StoredSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SkinLibraryScreen extends Screen {
    private static final int CARD_WIDTH = 148;
    private static final int CARD_HEIGHT = 174;
    private static final int GAP = 10;
    /** Ticks between refreshes of the player list, so joins and leaves show up while open. */
    private static final int PLAYER_REFRESH_TICKS = 40;

    private final SimpleSkinClient mod = SimpleSkinClient.get();
    private Tab tab = Tab.SAVED;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentTop;
    private int contentBottom;
    private int columns = 1;
    private int visibleRows = 1;
    /** Scroll position in whole rows: every rendered card is fully visible and fully clickable. */
    private int scrollRow;
    private int maxScrollRow;
    private int playerRefreshTimer;
    private int lastPlayerCount = -1;
    private EditBox searchField;
    private String searchTerm = "";
    private String status = "K opens Simple Skin anywhere in-game.";
    private List<Card> cards = List.of();

    public SkinLibraryScreen() {
        super(Component.literal("Simple Skin"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(850, width - 24);
        panelHeight = Math.min(510, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentTop = panelY + 74;
        contentBottom = panelY + panelHeight - 34;

        int tabX = panelX + 18;
        for (Tab target : Tab.values()) {
            int tabWidth = font.width(target.label) + 22;
            addTab(target, tabX, tabWidth);
            tabX += tabWidth + 6;
        }
        addRenderableWidget(new ThemedButton(panelX + panelWidth - 96, panelY + 42, 78, 22,
                Component.literal("Import PNG"), ignored -> importSkin(), false)
                .tooltip(Component.literal("Load a 64x64 skin PNG from your computer")));
        addRenderableWidget(new ThemedButton(panelX + panelWidth - 180, panelY + 42, 78, 22,
                Component.literal("Shuffle"), ignored -> mod.equipRandom(this::setStatus), false)
                .tooltip(Component.literal("Equip a random saved skin")));

        if (tab == Tab.STEAL) {
            // Searching by name reaches players who are not on this server at all.
            searchField = new EditBox(font, panelX + 18, contentTop - 26, 150, 18,
                    Component.literal("Player name"));
            searchField.setMaxLength(16);
            searchField.setValue(searchTerm);
            searchField.setResponder(value -> searchTerm = value);
            addRenderableWidget(searchField);
            addRenderableWidget(new ThemedButton(panelX + 174, contentTop - 26, 70, 18,
                    Component.literal("Search"), ignored -> searchByName(), false)
                    .tooltip(Component.literal("Look a player up through Mojang")));
        }

        cards = buildCards();
        columns = Math.max(1, (panelWidth - 36 + GAP) / (CARD_WIDTH + GAP));
        visibleRows = Math.max(1, (contentBottom - contentTop + GAP) / (CARD_HEIGHT + GAP));
        int rows = (cards.size() + columns - 1) / columns;
        maxScrollRow = Math.max(0, rows - visibleRows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow));

        for (int index = 0; index < cards.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            Card card = cards.get(index);
            card.x = panelX + 18 + column * (CARD_WIDTH + GAP);
            card.y = contentTop + (row - scrollRow) * (CARD_HEIGHT + GAP);
            card.visible = row >= scrollRow && row < scrollRow + visibleRows;
            if (card.visible) {
                addCardButtons(card);
            }
        }

        if (mod.visibility().rejoinPending()) {
            addRenderableWidget(new ThemedButton(panelX + panelWidth - 96, panelY + panelHeight - 30, 78, 20,
                    Component.literal("Rejoin now"), ignored -> mod.visibility().rejoinNow(this::setStatus), true)
                    .tooltip(Component.literal("Reconnect so other players load your new skin")));
        }
    }

    private void addTab(Tab target, int x, int tabWidth) {
        addRenderableWidget(new ThemedButton(x, panelY + 42, tabWidth, 22, Component.literal(target.label),
                ignored -> {
                    if (tab != target) {
                        tab = target;
                        scrollRow = 0;
                    }
                    rebuildWidgets();
                }, tab == target));
    }

    private void addCardButtons(Card card) {
        int y = card.y + CARD_HEIGHT - 29;
        addRenderableWidget(new ThemedButton(card.x + 8, y, 52, 20, Component.literal("Equip"),
                ignored -> equip(card), true));
        boolean alreadySaved = card.skin != null && card.skin.saved();
        ThemedButton save = new ThemedButton(card.x + 64, y, 42, 20,
                Component.literal(alreadySaved ? "Saved" : "Save"), ignored -> save(card), false);
        if (alreadySaved) {
            save.disabled();
        }
        addRenderableWidget(save);
        addRenderableWidget(new ThemedButton(card.x + 110, y, 30, 20, Component.literal("⚙"),
                ignored -> settings(card), false).tooltip(Component.literal("Skin settings")));
    }

    private List<Card> buildCards() {
        if (tab == Tab.HISTORY) {
            return mod.repository().history().stream().map(Card::skin).toList();
        }
        if (tab == Tab.SAVED) {
            return mod.repository().savedSkins().stream().map(Card::skin).toList();
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return List.of();
        }
        List<PlayerInfo> players = new ArrayList<>(client.getConnection().getOnlinePlayers());
        players.sort(Comparator.comparing(entry -> entry.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        return players.stream()
                .filter(entry -> client.player == null || !entry.getProfile().id().equals(client.player.getUUID()))
                .map(Card::player)
                .toList();
    }

    private void equip(Card card) {
        if (card.skin != null) {
            mod.equip(card.skin, this::setStatus);
            return;
        }
        mod.copyAndEquip(card.player, this::setStatus);
    }

    private void save(Card card) {
        if (card.skin != null) {
            card.skin.setSaved(true);
            mod.repository().update(card.skin);
            setStatus("Saved " + card.skin.name() + " to Change Skin.");
            rebuildWidgets();
            return;
        }
        mod.copy(card.player, true).whenComplete((skin, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                setStatus(SimpleSkinClient.describe(error, card.player));
            } else {
                setStatus("Saved " + skin.name() + " to Change Skin.");
                rebuildWidgets();
            }
        }));
    }

    private void settings(Card card) {
        if (card.skin != null) {
            minecraft.gui.setScreen(new SkinSettingsScreen(this, card.skin));
            return;
        }
        setStatus("Preparing " + card.player.getProfile().name() + "...");
        mod.copy(card.player, false).whenComplete((skin, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                setStatus(SimpleSkinClient.describe(error, card.player));
            } else {
                minecraft.gui.setScreen(new SkinSettingsScreen(this, skin));
            }
        }));
    }

    /**
     * Looks a name up through Mojang and adds that player's skin to the library, so a skin can be
     * taken from someone who is not on this server.
     */
    private void searchByName() {
        String name = searchTerm.strip();
        if (name.isEmpty()) {
            setStatus("Type a player name first.");
            return;
        }
        setStatus("Looking up " + name + "...");
        mod.profiles().lookup(name)
                .thenCompose(profile -> mod.downloads().download(profile))
                .whenComplete((downloaded, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        setStatus(SimpleSkinClient.describe(error, null));
                        return;
                    }
                    try {
                        StoredSkin found = mod.repository().add(downloaded.png(), downloaded.playerName(),
                                downloaded.playerName(), downloaded.model(), true);
                        setStatus("Saved " + found.name() + " to Change Skin.");
                        tab = Tab.SAVED;
                        rebuildWidgets();
                    } catch (IOException exception) {
                        setStatus("Could not store that skin: " + exception.getMessage());
                    }
                }));
    }

    private void importSkin() {
        setStatus("Choose a skin PNG...");
        SkinFilePicker.pickPngAsync(chosen -> {
            if (chosen == null) {
                setStatus("Import cancelled.");
                return;
            }
            try {
                StoredSkin imported = mod.repository().importFile(chosen, true);
                setStatus("Imported " + imported.name() + ".");
                tab = Tab.SAVED;
                rebuildWidgets();
            } catch (IOException exception) {
                setStatus("Import failed: " + exception.getMessage());
            }
        });
    }

    private void setStatus(String status) {
        this.status = status;
    }

    @Override
    public void tick() {
        super.tick();
        if (tab != Tab.STEAL) {
            return;
        }
        // Rebuild only when the roster actually changed, so the screen does not flicker.
        if (++playerRefreshTimer < PLAYER_REFRESH_TICKS) {
            return;
        }
        playerRefreshTimer = 0;
        Minecraft client = Minecraft.getInstance();
        int count = client.getConnection() == null ? 0 : client.getConnection().getOnlinePlayers().size();
        if (count != lastPlayerCount) {
            lastPlayerCount = count;
            rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, SimpleSkinTheme.BACKDROP);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SimpleSkinTheme.PANEL_EDGE);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, SimpleSkinTheme.PANEL);
        graphics.text(font, Component.literal("SIMPLE SKIN"), panelX + 18, panelY + 16, SimpleSkinTheme.PAPER, true);
        graphics.text(font, Component.literal(tab.subtitle), panelX + 112, panelY + 16, SimpleSkinTheme.PAPER_MUTED, false);
        drawVisibilityBadge(graphics);
        graphics.horizontalLine(panelX + 18, panelX + panelWidth - 18, panelY + 34, SimpleSkinTheme.PANEL_EDGE);

        graphics.enableScissor(panelX + 10, contentTop - 2, panelX + panelWidth - 10, contentBottom + 1);
        for (Card card : cards) {
            if (card.visible) {
                renderCard(graphics, card, mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        if (cards.isEmpty()) {
            String empty = switch (tab) {
                case HISTORY -> "Your equipped skins will appear here.";
                case SAVED -> "Steal a skin or import a PNG to start your collection.";
                case STEAL -> "Join a multiplayer server to see its players.";
            };
            graphics.centeredText(font, empty, width / 2, contentTop + 60, SimpleSkinTheme.PAPER_MUTED);
        }
        drawScrollbar(graphics);

        int statusWidth = panelWidth - 36 - (mod.visibility().rejoinPending() ? 86 : 0);
        graphics.text(font, font.plainSubstrByWidth(status, Math.max(20, statusWidth)),
                panelX + 18, panelY + panelHeight - 22, SimpleSkinTheme.PAPER_MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /** Shows where a skin change will end up, so "nobody else sees it" is never a surprise. */
    private void drawVisibilityBadge(GuiGraphicsExtractor graphics) {
        SimpleSkinConfig.Visibility visibility = mod.config().visibility();
        String label = "Visibility: " + visibility.label();
        int right = panelX + panelWidth - 18;
        int color = visibility == SimpleSkinConfig.Visibility.LOCAL_ONLY
                ? SimpleSkinTheme.PAPER_MUTED
                : SimpleSkinTheme.EQUIPPED;
        graphics.text(font, label, right - font.width(label), panelY + 16, color, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (maxScrollRow <= 0) {
            return;
        }
        int totalRows = maxScrollRow + visibleRows;
        int trackHeight = contentBottom - contentTop;
        int thumbHeight = Math.max(24, trackHeight * visibleRows / totalRows);
        int thumbY = contentTop + (trackHeight - thumbHeight) * scrollRow / maxScrollRow;
        graphics.fill(panelX + panelWidth - 8, contentTop, panelX + panelWidth - 5, contentBottom, 0xFF252B30);
        graphics.fill(panelX + panelWidth - 8, thumbY, panelX + panelWidth - 5, thumbY + thumbHeight, SimpleSkinTheme.COPPER);
    }

    private void renderCard(GuiGraphicsExtractor graphics, Card card, int mouseX, int mouseY) {
        boolean hovered = mouseX >= card.x && mouseX < card.x + CARD_WIDTH
                && mouseY >= card.y && mouseY < card.y + CARD_HEIGHT;
        boolean equipped = card.skin != null && card.skin == mod.equippedSkin();
        int border = equipped ? SimpleSkinTheme.EQUIPPED : (hovered ? SimpleSkinTheme.PAPER : SimpleSkinTheme.PAPER_MUTED);
        graphics.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + CARD_HEIGHT, border);
        graphics.fill(card.x + 1, card.y + 1, card.x + CARD_WIDTH - 1, card.y + CARD_HEIGHT - 1,
                hovered ? 0xFF2A3036 : SimpleSkinTheme.SURFACE);
        graphics.fill(card.x + 8, card.y + 8, card.x + CARD_WIDTH - 8, card.y + 116, 0xFF111519);

        Identifier texture = texture(card);
        if (texture != null) {
            if (card.skin != null) {
                SkinPreviewRenderer.draw(graphics, texture, card.skin.model(), card.x + CARD_WIDTH / 2, card.y + 15, 2);
            } else {
                SkinPreviewRenderer.drawHead(graphics, texture, card.x + 49, card.y + 26, 50);
            }
        } else {
            graphics.centeredText(font, "no preview", card.x + CARD_WIDTH / 2, card.y + 56, SimpleSkinTheme.PAPER_MUTED);
        }

        String name = card.skin != null ? card.skin.name() : card.player.getProfile().name();
        graphics.text(font, font.plainSubstrByWidth(name, CARD_WIDTH - 16),
                card.x + 8, card.y + 122, SimpleSkinTheme.PAPER, false);
        String source = describeSource(card);
        graphics.text(font, font.plainSubstrByWidth(source, CARD_WIDTH - 16),
                card.x + 8, card.y + 135, SimpleSkinTheme.PAPER_MUTED, false);
        if (card.skin != null && card.skin.keyCode() >= 0) {
            String key = InputConstants.Type.KEYSYM.getOrCreate(card.skin.keyCode()).getDisplayName().getString();
            graphics.text(font, font.plainSubstrByWidth("[" + key + "]", CARD_WIDTH - 16),
                    card.x + 8, card.y + 148, SimpleSkinTheme.COPPER, false);
        }
    }

    private String describeSource(Card card) {
        if (card.skin == null) {
            return "online now";
        }
        return card.skin.sourcePlayer() != null ? "from " + card.skin.sourcePlayer() : "imported";
    }

    private Identifier texture(Card card) {
        try {
            if (card.skin != null) {
                return mod.textures().getOrLoad(card.skin);
            }
            PlayerSkin skin = card.player.getSkin();
            return skin == null || skin.body() == null ? null : skin.body().texturePath();
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= contentTop && mouseY <= contentBottom) {
            int next = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) Math.signum(verticalAmount)));
            if (next != scrollRow) {
                scrollRow = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        HISTORY("History", "Recently equipped"),
        SAVED("Change Skin", "Your saved wardrobe"),
        STEAL("Steal Skin", "Players on this server");

        private final String label;
        private final String subtitle;

        Tab(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }
    }

    private static final class Card {
        private final StoredSkin skin;
        private final PlayerInfo player;
        private int x;
        private int y;
        private boolean visible;

        private Card(StoredSkin skin, PlayerInfo player) {
            this.skin = skin;
            this.player = player;
        }

        private static Card skin(StoredSkin skin) {
            return new Card(skin, null);
        }

        private static Card player(PlayerInfo player) {
            return new Card(null, player);
        }
    }
}
