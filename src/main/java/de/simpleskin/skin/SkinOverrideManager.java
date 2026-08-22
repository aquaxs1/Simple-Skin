package de.simpleskin.skin;

import de.simpleskin.data.SkinModel;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinOverrideManager {
    private static final Map<UUID, Override> OVERRIDES = new ConcurrentHashMap<>();

    private SkinOverrideManager() {
    }

    public static void set(UUID playerId, Identifier texture, SkinModel model) {
        set(playerId, texture, model, CapeOverride.keep(), null);
    }

    /**
     * @param cape what to do with the cape; {@code capeTexture} is only read for
     *     {@link CapeOverride#REPLACE} and may otherwise be {@code null}.
     */
    public static void set(UUID playerId, Identifier texture, SkinModel model, CapeOverride cape,
            Identifier capeTexture) {
        Override previous = OVERRIDES.get(playerId);
        if (previous != null && previous.texture().equals(texture) && previous.model() == model
                && previous.cape == cape && java.util.Objects.equals(previous.capeTexture, capeTexture)) {
            return;
        }
        OVERRIDES.put(playerId, new Override(texture, model, new ClientAsset.ResourceTexture(texture, texture),
                cape, capeTexture == null ? null : new ClientAsset.ResourceTexture(capeTexture, capeTexture),
                capeTexture));
    }

    /** How an override treats the cape slot. */
    public enum CapeOverride {
        /** Leave the profile's cape untouched. */
        KEEP,
        /** Render no cape at all. */
        HIDE,
        /** Render a specific cape texture. */
        REPLACE;

        static CapeOverride keep() {
            return KEEP;
        }
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
     * <p>This runs from {@code AbstractClientPlayer#getSkin}, which the renderer calls for every
     * visible player on every frame, so the derived {@link PlayerSkin} is memoised per override
     * and only rebuilt when the source skin actually changes. Cape, elytra and the {@code secure}
     * flag are carried over untouched: dropping them used to strip a player's cape and mark the
     * skin insecure the moment a skin was equipped. Minecraft's own {@code PlayerSkin#with} is not
     * used because it routes through {@code insecure(...)}, which clears that same flag.
     */
    public static PlayerSkin apply(UUID playerId, PlayerSkin original) {
        Override override = OVERRIDES.get(playerId);
        if (override == null || original == null) {
            return original;
        }
        return override.derive(original);
    }

    private static final class Override {
        private final Identifier texture;
        private final SkinModel model;
        private final ClientAsset.ResourceTexture body;
        private final CapeOverride cape;
        private final ClientAsset.ResourceTexture capeAsset;
        private final Identifier capeTexture;
        private volatile PlayerSkin cachedSource;
        private volatile PlayerSkin cachedResult;

        private Override(Identifier texture, SkinModel model, ClientAsset.ResourceTexture body,
                CapeOverride cape, ClientAsset.ResourceTexture capeAsset, Identifier capeTexture) {
            this.texture = texture;
            this.model = model;
            this.body = body;
            this.cape = cape;
            this.capeAsset = capeAsset;
            this.capeTexture = capeTexture;
        }

        private Identifier texture() {
            return texture;
        }

        private SkinModel model() {
            return model;
        }

        private PlayerSkin derive(PlayerSkin original) {
            PlayerSkin source = cachedSource;
            PlayerSkin result = cachedResult;
            if (source != null && source.equals(original) && result != null) {
                return result;
            }
            ClientAsset.Texture capeSlot = switch (cape) {
                case KEEP -> original.cape();
                case HIDE -> null;
                case REPLACE -> capeAsset != null ? capeAsset : original.cape();
            };
            PlayerSkin derived = new PlayerSkin(body, capeSlot, original.elytra(),
                    model.toMinecraft(), original.secure());
            cachedSource = original;
            cachedResult = derived;
            return derived;
        }
    }
}
