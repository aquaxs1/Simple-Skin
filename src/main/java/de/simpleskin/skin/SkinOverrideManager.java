package de.simpleskin.skin;

import de.simpleskin.data.SkinModel;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinOverrideManager {
    private static final Map<UUID, Override> OVERRIDES = new ConcurrentHashMap<>();

    private SkinOverrideManager() {
    }

    public static void set(UUID playerId, Identifier texture, SkinModel model) {
        Override previous = OVERRIDES.get(playerId);
        if (previous != null && previous.texture().equals(texture) && previous.model() == model) {
            return;
        }
        OVERRIDES.put(playerId, new Override(texture, model,
                new AssetInfo.TextureAssetInfo(texture, texture)));
    }

    public static void clear(UUID playerId) {
        OVERRIDES.remove(playerId);
    }

    public static boolean has(UUID playerId) {
        return OVERRIDES.containsKey(playerId);
    }

    /**
     * Swaps the body texture of {@code original} for the equipped skin.
     *
     * <p>This runs from {@code AbstractClientPlayerEntity#getSkin}, which the renderer calls for
     * every visible player on every frame, so the derived {@link SkinTextures} is memoised per
     * override and only rebuilt when the source textures actually change. Cape, elytra and the
     * {@code secure} flag are carried over untouched: dropping them used to strip a player's cape
     * and mark the skin insecure the moment a skin was equipped.
     */
    public static SkinTextures apply(UUID playerId, SkinTextures original) {
        Override override = OVERRIDES.get(playerId);
        if (override == null || original == null) {
            return original;
        }
        return override.derive(original);
    }

    private static final class Override {
        private final Identifier texture;
        private final SkinModel model;
        private final AssetInfo.TextureAssetInfo body;
        private volatile SkinTextures cachedSource;
        private volatile SkinTextures cachedResult;

        private Override(Identifier texture, SkinModel model, AssetInfo.TextureAssetInfo body) {
            this.texture = texture;
            this.model = model;
            this.body = body;
        }

        private Identifier texture() {
            return texture;
        }

        private SkinModel model() {
            return model;
        }

        private SkinTextures derive(SkinTextures original) {
            SkinTextures source = cachedSource;
            SkinTextures result = cachedResult;
            if (source != null && source.equals(original) && result != null) {
                return result;
            }
            SkinTextures derived = new SkinTextures(body, original.cape(), original.elytra(),
                    model.toMinecraft(), original.secure());
            cachedSource = original;
            cachedResult = derived;
            return derived;
        }
    }
}
