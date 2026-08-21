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
        OVERRIDES.put(playerId, new Override(texture, model));
    }

    public static void clear(UUID playerId) {
        OVERRIDES.remove(playerId);
    }

    public static SkinTextures apply(UUID playerId, SkinTextures original) {
        Override override = OVERRIDES.get(playerId);
        if (override == null || original == null) {
            return original;
        }
        AssetInfo.TextureAssetInfo body = new AssetInfo.TextureAssetInfo(override.texture(), override.texture());
        return new SkinTextures(body, original.cape(), original.elytra(), override.model().toMinecraft(), false);
    }

    private record Override(Identifier texture, SkinModel model) {
    }
}
