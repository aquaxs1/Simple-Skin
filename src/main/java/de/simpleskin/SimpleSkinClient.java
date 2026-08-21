package de.simpleskin;

import de.simpleskin.data.SkinRepository;
import de.simpleskin.data.StoredSkin;
import de.simpleskin.screen.SkinLibraryScreen;
import de.simpleskin.skin.MinecraftSkinUploadService;
import de.simpleskin.skin.PlayerSkinDownloadService;
import de.simpleskin.skin.SkinOverrideManager;
import de.simpleskin.skin.SkinTextureStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SimpleSkinClient implements ClientModInitializer {
    public static final String MOD_ID = "simple_skin";
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
    private static SimpleSkinClient instance;

    private final SkinRepository repository = new SkinRepository();
    private final PlayerSkinDownloadService downloads = new PlayerSkinDownloadService();
    private final MinecraftSkinUploadService uploads = new MinecraftSkinUploadService();
    private final Set<Integer> pressedSkinKeys = new HashSet<>();
    private SkinTextureStore textures;
    private KeyBinding openMenu;

    public static SimpleSkinClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        repository.load();
        textures = new SkinTextureStore(repository);
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.simple_skin.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    public SkinRepository repository() {
        return repository;
    }

    public SkinTextureStore textures() {
        return textures;
    }

    public void equip(StoredSkin skin, Consumer<String> status) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            status.accept("Join a world or server before equipping a skin.");
            return;
        }
        try {
            SkinOverrideManager.set(client.player.getUuid(), textures.getOrLoad(skin), skin.model());
            repository.markEquipped(skin);
            status.accept("Equipped " + skin.name() + " locally. Updating your profile...");
            uploads.upload(skin, repository.imagePath(skin)).thenAccept(result ->
                    client.execute(() -> status.accept(result.message())));
        } catch (IOException exception) {
            status.accept("The local skin PNG could not be loaded.");
        }
    }

    public CompletableFuture<StoredSkin> copy(PlayerListEntry player, boolean save) {
        return downloads.download(player.getProfile()).thenApply(downloaded -> {
            try {
                return repository.add(downloaded.png(), downloaded.playerName(), downloaded.playerName(),
                        downloaded.model(), save);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    public void copyAndEquip(PlayerListEntry player, Consumer<String> status) {
        status.accept("Downloading " + player.getProfile().name() + "...");
        copy(player, false).whenComplete((skin, error) -> MinecraftClient.getInstance().execute(() -> {
            if (error != null) {
                status.accept("Could not download that player's skin.");
            } else {
                equip(skin, status);
            }
        }));
    }

    public void copyAndSave(PlayerListEntry player, Consumer<String> status) {
        status.accept("Saving " + player.getProfile().name() + "...");
        copy(player, true).whenComplete((skin, error) -> MinecraftClient.getInstance().execute(() -> {
            if (error != null) {
                status.accept("Could not download that player's skin.");
            } else {
                status.accept("Saved " + skin.name() + " to Change Skin.");
            }
        }));
    }

    private void onEndTick(MinecraftClient client) {
        while (openMenu.wasPressed()) {
            if (client.player != null) {
                client.setScreen(new SkinLibraryScreen());
            }
        }
        if (client.currentScreen != null || client.player == null) {
            pressedSkinKeys.clear();
            return;
        }
        Set<Integer> downNow = new HashSet<>();
        for (StoredSkin skin : repository.savedSkins()) {
            int key = skin.keyCode();
            if (key >= 0 && InputUtil.isKeyPressed(client.getWindow(), key)) {
                downNow.add(key);
                if (!pressedSkinKeys.contains(key)) {
                    equip(skin, message -> client.player.sendMessage(Text.literal("[Simple Skin] " + message), true));
                }
            }
        }
        pressedSkinKeys.clear();
        pressedSkinKeys.addAll(downNow);
    }
}
