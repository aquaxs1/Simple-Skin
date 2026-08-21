package de.simpleskin.screen;

import de.simpleskin.SimpleSkinClient;
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

    private final SimpleSkinClient mod = SimpleSkinClient.get();
    private Tab tab = Tab.SAVED;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentTop;
    private int contentBottom;
    private int scroll;
    private int maxScroll;
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

        addTab(Tab.HISTORY, panelX + 18, "History");
        addTab(Tab.SAVED, panelX + 122, "Change Skin");
        addTab(Tab.STEAL, panelX + 250, "Steal Skin");

        cards = buildCards();
        int columns = Math.max(1, (panelWidth - 36 + GAP) / (CARD_WIDTH + GAP));
        int rows = (cards.size() + columns - 1) / columns;
        maxScroll = Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP - (contentBottom - contentTop));
        scroll = Math.min(scroll, maxScroll);

        for (int index = 0; index < cards.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = panelX + 18 + column * (CARD_WIDTH + GAP);
            int y = contentTop + row * (CARD_HEIGHT + GAP) - scroll;
            Card card = cards.get(index);
            card.x = x;
            card.y = y;
            if (y >= contentTop && y + CARD_HEIGHT <= contentBottom) {
                addCardButtons(card);
            }
        }
    }

    private void addTab(Tab target, int x, String label) {
        ThemedButton button = new ThemedButton(x, panelY + 42, target == Tab.SAVED ? 116 : 92, 22,
                Text.literal(label), ignored -> {
                    tab = target;
                    scroll = 0;
                    clearAndInit();
                }, tab == target);
        addDrawableChild(button);
    }

    private void addCardButtons(Card card) {
        int y = card.y + CARD_HEIGHT - 29;
        ThemedButton equip = new ThemedButton(card.x + 8, y, 56, 20, Text.literal("Equip"), ignored -> equip(card), true);
        ThemedButton save = new ThemedButton(card.x + 68, y, 43, 20,
                Text.literal(card.skin != null && card.skin.saved() ? "Saved" : "Save"), ignored -> save(card), false);
        if (card.skin != null && card.skin.saved()) {
            save.active = false;
        }
        ThemedButton settings = new ThemedButton(card.x + 115, y, 25, 20, Text.literal("\u2699"), ignored -> settings(card), false)
                .tooltip(Text.literal("Skin settings"));
        addDrawableChild(equip);
        addDrawableChild(save);
        addDrawableChild(settings);
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
                setStatus("Could not download that player's skin.");
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
                setStatus("Could not download that player's skin.");
            } else {
                client.setScreen(new SkinSettingsScreen(this, skin));
            }
        }));
    }

    private void setStatus(String status) {
        this.status = status;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SimpleSkinTheme.BACKDROP);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SimpleSkinTheme.PANEL_EDGE);
        context.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, SimpleSkinTheme.PANEL);
        context.drawTextWithShadow(textRenderer, Text.literal("SIMPLE SKIN"), panelX + 18, panelY + 16, SimpleSkinTheme.PAPER);
        context.drawText(textRenderer, Text.literal(tab.subtitle), panelX + 112, panelY + 16, SimpleSkinTheme.PAPER_MUTED, false);
        context.drawHorizontalLine(panelX + 18, panelX + panelWidth - 18, panelY + 34, SimpleSkinTheme.PANEL_EDGE);

        context.enableScissor(panelX + 10, contentTop - 2, panelX + panelWidth - 10, contentBottom + 1);
        for (Card card : cards) {
            if (card.y + CARD_HEIGHT >= contentTop && card.y <= contentBottom) {
                renderCard(context, card, mouseX, mouseY);
            }
        }
        context.disableScissor();

        if (cards.isEmpty()) {
            String empty = switch (tab) {
                case HISTORY -> "Your equipped skins will appear here.";
                case SAVED -> "Save a server player's skin to start your collection.";
                case STEAL -> "Join a multiplayer server to see its players.";
            };
            context.drawCenteredTextWithShadow(textRenderer, empty, width / 2, contentTop + 60, SimpleSkinTheme.PAPER_MUTED);
        }
        if (maxScroll > 0) {
            int trackHeight = contentBottom - contentTop;
            int thumbHeight = Math.max(24, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = contentTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            context.fill(panelX + panelWidth - 8, contentTop, panelX + panelWidth - 5, contentBottom, 0xFF252B30);
            context.fill(panelX + panelWidth - 8, thumbY, panelX + panelWidth - 5, thumbY + thumbHeight, SimpleSkinTheme.COPPER);
        }
        context.drawText(textRenderer, Text.literal(status), panelX + 18, panelY + panelHeight - 22,
                SimpleSkinTheme.PAPER_MUTED, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCard(DrawContext context, Card card, int mouseX, int mouseY) {
        boolean hovered = mouseX >= card.x && mouseX < card.x + CARD_WIDTH && mouseY >= card.y && mouseY < card.y + CARD_HEIGHT;
        context.fill(card.x, card.y, card.x + CARD_WIDTH, card.y + CARD_HEIGHT,
                hovered ? SimpleSkinTheme.PAPER : SimpleSkinTheme.PAPER_MUTED);
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
        }
        String name = card.skin != null ? card.skin.name() : card.player.getProfile().name();
        context.drawText(textRenderer, trim(name, 19), card.x + 8, card.y + 122, SimpleSkinTheme.PAPER, false);
        String source = card.skin != null && card.skin.sourcePlayer() != null ? "from " + card.skin.sourcePlayer() : "online now";
        context.drawText(textRenderer, trim(source, 22), card.x + 8, card.y + 135, SimpleSkinTheme.PAPER_MUTED, false);
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

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 1)) + "...";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= contentTop && mouseY <= contentBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 42));
            clearAndInit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private enum Tab {
        HISTORY("Recently equipped"),
        SAVED("Your saved wardrobe"),
        STEAL("Players on this server");

        private final String subtitle;

        Tab(String subtitle) {
            this.subtitle = subtitle;
        }
    }

    private static final class Card {
        private final StoredSkin skin;
        private final PlayerListEntry player;
        private int x;
        private int y;

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
