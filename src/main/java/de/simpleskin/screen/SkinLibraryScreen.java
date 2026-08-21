package de.simpleskin.screen;

import de.simpleskin.SimpleSkinClient;
import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.StoredSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
    private String status = "K opens Simple Skin anywhere in-game.";
    private List<Card> cards = List.of();

    public SkinLibraryScreen() {
        super(Text.literal("Simple Skin"));
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
            int tabWidth = textRenderer.getWidth(target.label) + 22;
            addTab(target, tabX, tabWidth);
            tabX += tabWidth + 6;
        }
        addDrawableChild(new ThemedButton(panelX + panelWidth - 96, panelY + 42, 78, 22,
                Text.literal("Import PNG"), ignored -> importSkin(), false)
                .tooltip(Text.literal("Load a 64x64 skin PNG from your computer")));

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
            addDrawableChild(new ThemedButton(panelX + panelWidth - 96, panelY + panelHeight - 30, 78, 20,
                    Text.literal("Rejoin now"), ignored -> mod.visibility().rejoinNow(this::setStatus), true)
                    .tooltip(Text.literal("Reconnect so other players load your new skin")));
        }
    }

    private void addTab(Tab target, int x, int tabWidth) {
        addDrawableChild(new ThemedButton(x, panelY + 42, tabWidth, 22, Text.literal(target.label), ignored -> {
            if (tab != target) {
                tab = target;
                scrollRow = 0;
            }
            clearAndInit();
        }, tab == target));
    }

    private void addCardButtons(Card card) {
        int y = card.y + CARD_HEIGHT - 29;
        addDrawableChild(new ThemedButton(card.x + 8, y, 52, 20, Text.literal("Equip"), ignored -> equip(card), true));
        boolean alreadySaved = card.skin != null && card.skin.saved();
        ThemedButton save = new ThemedButton(card.x + 64, y, 42, 20,
                Text.literal(alreadySaved ? "Saved" : "Save"), ignored -> save(card), false);
        if (alreadySaved) {
            save.disabled();
        }
        addDrawableChild(save);
        addDrawableChild(new ThemedButton(card.x + 110, y, 30, 20, Text.literal("⚙"),
                ignored -> settings(card), false).tooltip(Text.literal("Skin settings")));
    }

    private List<Card> buildCards() {
        if (tab == Tab.HISTORY) {
            return mod.repository().history().stream().map(Card::skin).toList();
        }
        if (tab == Tab.SAVED) {
            return mod.repository().savedSkins().stream().map(Card::skin).toList();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return List.of();
        }
        List<PlayerListEntry> players = new ArrayList<>(client.getNetworkHandler().getPlayerList());
        players.sort(Comparator.comparing(entry -> entry.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        return players.stream()
                .filter(entry -> client.player == null || !entry.getProfile().id().equals(client.player.getUuid()))
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
            clearAndInit();
            return;
        }
        mod.copy(card.player, true).whenComplete((skin, error) -> MinecraftClient.getInstance().execute(() -> {
            if (error != null) {
                setStatus(SimpleSkinClient.describe(error, card.player));
            } else {
                setStatus("Saved " + skin.name() + " to Change Skin.");
                clearAndInit();
            }
        }));
    }

    private void settings(Card card) {
        if (card.skin != null) {
            client.setScreen(new SkinSettingsScreen(this, card.skin));
            return;
        }
        setStatus("Preparing " + card.player.getProfile().name() + "...");
        mod.copy(card.player, false).whenComplete((skin, error) -> MinecraftClient.getInstance().execute(() -> {
            if (error != null) {
                setStatus(SimpleSkinClient.describe(error, card.player));
            } else {
                client.setScreen(new SkinSettingsScreen(this, skin));
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
                clearAndInit();
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
        MinecraftClient minecraft = MinecraftClient.getInstance();
        int count = minecraft.getNetworkHandler() == null ? 0 : minecraft.getNetworkHandler().getPlayerList().size();
        if (count != lastPlayerCount) {
            lastPlayerCount = count;
            clearAndInit();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SimpleSkinTheme.BACKDROP);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SimpleSkinTheme.PANEL_EDGE);
        context.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, SimpleSkinTheme.PANEL);
        context.drawTextWithShadow(textRenderer, Text.literal("SIMPLE SKIN"), panelX + 18, panelY + 16, SimpleSkinTheme.PAPER);
        context.drawText(textRenderer, Text.literal(tab.subtitle), panelX + 112, panelY + 16, SimpleSkinTheme.PAPER_MUTED, false);
        drawVisibilityBadge(context);
        context.drawHorizontalLine(panelX + 18, panelX + panelWidth - 18, panelY + 34, SimpleSkinTheme.PANEL_EDGE);

        context.enableScissor(panelX + 10, contentTop - 2, panelX + panelWidth - 10, contentBottom + 1);
        for (Card card : cards) {
            if (card.visible) {
                renderCard(context, card, mouseX, mouseY);
            }
        }
        context.disableScissor();

        if (cards.isEmpty()) {
            String empty = switch (tab) {
                case HISTORY -> "Your equipped skins will appear here.";
                case SAVED -> "Steal a skin or import a PNG to start your collection.";
                case STEAL -> "Join a multiplayer server to see its players.";
            };
            context.drawCenteredTextWithShadow(textRenderer, empty, width / 2, contentTop + 60, SimpleSkinTheme.PAPER_MUTED);
        }
        drawScrollbar(context);

        int statusWidth = panelWidth - 36 - (mod.visibility().rejoinPending() ? 86 : 0);
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(status, Math.max(20, statusWidth))),
                panelX + 18, panelY + panelHeight - 22, SimpleSkinTheme.PAPER_MUTED, false);
        super.render(context, mouseX, mouseY, delta);
    }

    /** Shows where a skin change will end up, so "nobody else sees it" is never a surprise. */
    private void drawVisibilityBadge(DrawContext context) {
        SimpleSkinConfig.Visibility visibility = mod.config().visibility();
        String label = "Visibility: " + visibility.label();
        int textWidth = textRenderer.getWidth(label);
        int right = panelX + panelWidth - 18;
        int color = visibility == SimpleSkinConfig.Visibility.LOCAL_ONLY
                ? SimpleSkinTheme.PAPER_MUTED
                : SimpleSkinTheme.EQUIPPED;
        context.drawText(textRenderer, Text.literal(label), right - textWidth, panelY + 16, color, false);
    }

    private void drawScrollbar(DrawContext context) {
        if (maxScrollRow <= 0) {
            return;
        }
        int totalRows = maxScrollRow + visibleRows;
        int trackHeight = contentBottom - contentTop;
        int thumbHeight = Math.max(24, trackHeight * visibleRows / totalRows);
        int thumbY = contentTop + (trackHeight - thumbHeight) * scrollRow / maxScrollRow;
        context.fill(panelX + panelWidth - 8, contentTop, panelX + panelWidth - 5, contentBottom, 0xFF252B30);
        context.fill(panelX + panelWidth - 8, thumbY, panelX + panelWidth - 5, thumbY + thumbHeight, SimpleSkinTheme.COPPER);
    }

    private void renderCard(DrawContext context, Card card, int mouseX, int mouseY) {
        boolean hovered = mouseX >= card.x && mouseX < card.x + CARD_WIDTH
                && mouseY >= card.y && mouseY < card.y + CARD_HEIGHT;
        boolean equipped = card.skin != null && card.skin == mod.equippedSkin();
        int border = equipped ? SimpleSkinTheme.EQUIPPED : (hovered ? SimpleSkinTheme.PAPER : SimpleSkinTheme.PAPER_MUTED);
        context.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + CARD_HEIGHT, border);
        context.fill(card.x + 1, card.y + 1, card.x + CARD_WIDTH - 1, card.y + CARD_HEIGHT - 1,
                hovered ? 0xFF2A3036 : SimpleSkinTheme.SURFACE);
        context.fill(card.x + 8, card.y + 8, card.x + CARD_WIDTH - 8, card.y + 116, 0xFF111519);

        Identifier texture = texture(card);
        if (texture != null) {
            if (card.skin != null) {
                SkinPreviewRenderer.draw(context, texture, card.skin.model(), card.x + CARD_WIDTH / 2, card.y + 15, 2);
            } else {
                SkinPreviewRenderer.drawHead(context, texture, card.x + 49, card.y + 26, 50);
            }
        } else {
            context.drawCenteredTextWithShadow(textRenderer, "no preview", card.x + CARD_WIDTH / 2,
                    card.y + 56, SimpleSkinTheme.PAPER_MUTED);
        }

        String name = card.skin != null ? card.skin.name() : card.player.getProfile().name();
        context.drawText(textRenderer, textRenderer.trimToWidth(name, CARD_WIDTH - 16),
                card.x + 8, card.y + 122, SimpleSkinTheme.PAPER, false);
        String source = describeSource(card);
        context.drawText(textRenderer, textRenderer.trimToWidth(source, CARD_WIDTH - 16),
                card.x + 8, card.y + 135, SimpleSkinTheme.PAPER_MUTED, false);
        if (card.skin != null && card.skin.keyCode() >= 0) {
            String key = net.minecraft.client.util.InputUtil.Type.KEYSYM
                    .createFromCode(card.skin.keyCode()).getLocalizedText().getString();
            context.drawText(textRenderer, textRenderer.trimToWidth("[" + key + "]", CARD_WIDTH - 16),
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
            SkinTextures textures = card.player.getSkinTextures();
            return textures == null || textures.body() == null ? null : textures.body().texturePath();
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
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
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
        private final PlayerListEntry player;
        private int x;
        private int y;
        private boolean visible;

        private Card(StoredSkin skin, PlayerListEntry player) {
            this.skin = skin;
            this.player = player;
        }

        private static Card skin(StoredSkin skin) {
            return new Card(skin, null);
        }

        private static Card player(PlayerListEntry player) {
            return new Card(null, player);
        }
    }
}
