package de.simpleskin;

import com.mojang.blaze3d.platform.InputConstants;
import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.SkinRepository;
import de.simpleskin.data.StoredSkin;
import de.simpleskin.screen.SkinLibraryScreen;
import de.simpleskin.skin.MinecraftSkinUploadService;
import de.simpleskin.skin.PlayerSkinDownloadService;
import de.simpleskin.skin.SkinOverrideManager;
import de.simpleskin.skin.SkinTextureStore;
import de.simpleskin.skin.SkinVisibilityService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SimpleSkinClient implements ClientModInitializer {
    public static final String MOD_ID = "simple_skin";
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static SimpleSkinClient instance;

    private final SkinRepository repository = new SkinRepository();
    private final SimpleSkinConfig config = new SimpleSkinConfig();
    private final PlayerSkinDownloadService downloads = new PlayerSkinDownloadService();
    private final MinecraftSkinUploadService uploads = new MinecraftSkinUploadService();
    /** Hotkeys held down on the previous tick, so holding a key equips once instead of every tick. */
    private final Set<Integer> pressedSkinKeys = new HashSet<>();
    private SkinVisibilityService visibility;
    private SkinTextureStore textures;
    private KeyMapping openMenu;
    /** The skin the local override currently shows, or {@code null} when nothing is overridden. */
    private StoredSkin equipped;

    public static SimpleSkinClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        repository.load();
        config.load();
        textures = new SkinTextureStore(repository);
        visibility = new SkinVisibilityService(uploads, downloads, config);
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.simple_skin.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    public SkinRepository repository() {
        return repository;
    }

    public SimpleSkinConfig config() {
        return config;
    }

    public SkinTextureStore textures() {
        return textures;
    }

    public SkinVisibilityService visibility() {
        return visibility;
    }

    public void equip(StoredSkin skin, Consumer<String> status) {
        equip(skin, status, true);
    }

    /**
     * Equips a skin: renders it here immediately, then hands it to {@link SkinVisibilityService}
     * so other players get it too.
     */
    public void equip(StoredSkin skin, Consumer<String> status, boolean publish) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            status.accept("Join a world or server before equipping a skin.");
            return;
        }
        Identifier texture;
        try {
            texture = textures.getOrLoad(skin);
        } catch (IOException exception) {
            status.accept("The local skin PNG could not be loaded.");
            return;
        }
        SkinOverrideManager.set(client.player.getUUID(), texture, skin.model());
        equipped = skin;
        repository.markEquipped(skin);
        status.accept("Equipped " + skin.name() + ".");
        if (publish) {
            visibility.publish(skin, repository.imagePath(skin), status);
        }
    }

    /** Drops the local override so the player renders with whatever Mojang serves for them. */
    public void unequip(Consumer<String> status) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            status.accept("Join a world or server first.");
            return;
        }
        SkinOverrideManager.clear(client.player.getUUID());
        equipped = null;
        status.accept("Local override removed; showing your profile skin.");
    }

    public boolean hasLocalOverride() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && SkinOverrideManager.has(client.player.getUUID());
    }

    /** The skin the player is wearing right now, or {@code null} if none is overridden. */
    public StoredSkin equippedSkin() {
        return hasLocalOverride() ? equipped : null;
    }

    /**
     * Re-applies {@code skin} after its PNG or arm model changed. Does nothing unless that skin is
     * the one actually being worn, so editing one entry cannot swap the player onto another.
     */
    public void refresh(StoredSkin skin) {
        textures.invalidate(skin);
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || equippedSkin() != skin) {
            return;
        }
        try {
            SkinOverrideManager.set(client.player.getUUID(), textures.getOrLoad(skin), skin.model());
        } catch (IOException ignored) {
            // The card renderer reports the broken PNG; nothing useful to do from here.
        }
    }

    public CompletableFuture<StoredSkin> copy(PlayerInfo player, boolean save) {
        return downloads.download(player.getProfile()).thenApply(downloaded -> {
            try {
                return repository.add(downloaded.png(), downloaded.playerName(), downloaded.playerName(),
                        downloaded.model(), save);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    public void copyAndEquip(PlayerInfo player, Consumer<String> status) {
        status.accept("Downloading " + player.getProfile().name() + "...");
        copy(player, false).whenComplete((skin, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                status.accept(describe(error, player));
            } else {
                equip(skin, status);
            }
        }));
    }

    public void copyAndSave(PlayerInfo player, Consumer<String> status) {
        status.accept("Saving " + player.getProfile().name() + "...");
        copy(player, true).whenComplete((skin, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                status.accept(describe(error, player));
            } else {
                status.accept("Saved " + skin.name() + " to Change Skin.");
            }
        }));
    }

    /** Surfaces the real reason a copy failed instead of one generic message. */
    public static String describe(Throwable error, PlayerInfo player) {
        Throwable cause = error;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        String name = player == null ? "that player" : player.getProfile().name();
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? "Could not download " + name + "'s skin."
                : "Could not copy " + name + ": " + message;
    }

    /** Reports the vanilla key binding that already uses {@code keyCode}, if any. */
    public static Optional<KeyMapping> conflictingKeyBinding(int keyCode) {
        Minecraft client = Minecraft.getInstance();
        if (keyCode < 0 || client.options == null) {
            return Optional.empty();
        }
        String target = InputConstants.Type.KEYSYM.getOrCreate(keyCode).getName();
        for (KeyMapping binding : client.options.keyMappings) {
            if (!binding.isUnbound() && binding.saveString().equals(target)) {
                return Optional.of(binding);
            }
        }
        return Optional.empty();
    }

    private void onEndTick(Minecraft client) {
        while (openMenu.consumeClick()) {
            if (client.player != null) {
                client.gui.setScreen(new SkinLibraryScreen());
            }
        }
        if (client.player == null) {
            pressedSkinKeys.clear();
            // Leaving the server means the next login re-reads the profile, so nothing is pending.
            visibility.clearRejoinPending();
            return;
        }

        // Hotkeys are tracked even while a screen is open, so closing a screen with the key still
        // held does not immediately re-equip; only the transition from up to down equips.
        boolean canFire = client.gui.screen() == null;
        Set<Integer> downNow = new HashSet<>();
        StoredSkin pending = null;
        for (StoredSkin skin : repository.savedSkins()) {
            int key = skin.keyCode();
            if (key < 0 || !InputConstants.isKeyDown(client.getWindow(), key)) {
                continue;
            }
            downNow.add(key);
            if (canFire && pending == null && !pressedSkinKeys.contains(key)) {
                pending = skin;
            }
        }
        pressedSkinKeys.clear();
        pressedSkinKeys.addAll(downNow);

        if (pending != null) {
            equip(pending, message -> client.player.sendOverlayMessage(Component.literal("[Simple Skin] " + message)),
                    config.uploadOnHotkey());
        }
    }
}
