package de.simpleskin.skin;

import de.simpleskin.data.SkinRepository;
import de.simpleskin.data.StoredSkin;
import de.simpleskin.mixin.PlayerSkinTextureDownloaderInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SkinTextureStore {
    private final SkinRepository repository;
    private final Map<String, Identifier> textures = new HashMap<>();
    /**
     * Skins whose PNG could not be decoded. {@code getOrLoad} is called from the card renderer,
     * so without this a single broken file would be re-read from disk on every rendered frame.
     */
    private final Set<String> failed = new HashSet<>();

    public SkinTextureStore(SkinRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers the skin PNG with the texture manager and returns its identifier.
     *
     * <p>Must be called on the render thread; {@code TextureManager#registerTexture} is not
     * thread safe, which is why this is no longer backed by a concurrent map.
     */
    public Identifier getOrLoad(StoredSkin skin) throws IOException {
        Identifier cached = textures.get(skin.id());
        if (cached != null) {
            return cached;
        }
        if (failed.contains(skin.id())) {
            throw new IOException("The skin PNG for " + skin.name() + " could not be loaded");
        }
        Identifier identifier = Identifier.of("simple_skin", "skins/" + skin.id().replace("-", ""));
        try (InputStream input = Files.newInputStream(repository.imagePath(skin))) {
            NativeImage image = NativeImage.read(input);
            // remapTexture converts legacy 64x32 skins and closes the image it replaces.
            image = PlayerSkinTextureDownloaderInvoker.simpleSkin$remapTexture(image, skin.name());
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Simple Skin: " + skin.name(), image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
        } catch (IOException | RuntimeException exception) {
            failed.add(skin.id());
            throw exception instanceof IOException io ? io
                    : new IOException("The skin PNG for " + skin.name() + " could not be decoded", exception);
        }
        textures.put(skin.id(), identifier);
        return identifier;
    }

    /** Drops the cached GPU texture for a skin so the next lookup re-reads the PNG. */
    public void invalidate(StoredSkin skin) {
        failed.remove(skin.id());
        Identifier identifier = textures.remove(skin.id());
        if (identifier != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(identifier);
        }
    }
}
