package de.simpleskin.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads and registers cape textures so a chosen cape shows up locally right away, instead of
 * only after the profile change has propagated.
 */
public final class CapeTextureStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");

    private final PlayerSkinDownloadService downloads;
    private final Map<String, Identifier> textures = new HashMap<>();

    public CapeTextureStore(PlayerSkinDownloadService downloads) {
        this.downloads = downloads;
    }

    public Optional<Identifier> cached(String capeId) {
        return Optional.ofNullable(textures.get(capeId));
    }

    /**
     * Fetches a cape PNG and registers it. The returned future completes on the client thread
     * with the identifier, or empty when the cape could not be loaded.
     */
    public CompletableFuture<Optional<Identifier>> load(String capeId, String url) {
        Identifier cached = textures.get(capeId);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Minecraft client = Minecraft.getInstance();
        return downloads.fetch(URI.create(url))
                .thenApply(png -> {
                    // Registering a texture has to happen on the render thread.
                    CompletableFuture<Optional<Identifier>> done = new CompletableFuture<>();
                    client.execute(() -> done.complete(register(capeId, png)));
                    return done;
                })
                .thenCompose(future -> future)
                .exceptionally(error -> {
                    LOGGER.warn("Could not download the cape {}", capeId, error);
                    return Optional.empty();
                });
    }

    private Optional<Identifier> register(String capeId, byte[] png) {
        Identifier identifier = Identifier.fromNamespaceAndPath("simple_skin",
                "capes/" + capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", ""));
        try (ByteArrayInputStream input = new ByteArrayInputStream(png)) {
            // Capes are not skins, so they skip the 64x64 check and the legacy skin conversion.
            NativeImage image = NativeImage.read(input);
            Minecraft.getInstance().getTextureManager()
                    .register(identifier, new DynamicTexture(() -> "Simple Skin cape", image));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not decode the cape {}", capeId, exception);
            return Optional.empty();
        }
        textures.put(capeId, identifier);
        return Optional.of(identifier);
    }
}
