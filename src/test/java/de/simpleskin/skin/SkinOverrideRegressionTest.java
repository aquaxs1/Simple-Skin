package de.simpleskin.skin;

import de.simpleskin.data.SkinModel;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class SkinOverrideRegressionTest {
    private SkinOverrideRegressionTest() {
    }

    public static void main(String[] args) {
        UUID playerId = UUID.fromString("7aa56f75-86a4-4bfe-9d9d-801c03e9d8e1");
        Identifier registeredTexture = Identifier.of("simple_skin", "skins/regression");
        SkinTextures original = new SkinTextures(
                new AssetInfo.TextureAssetInfo(Identifier.of("minecraft", "entity/player/wide/steve")),
                null,
                null,
                PlayerSkinType.WIDE,
                true);

        SkinOverrideManager.set(playerId, registeredTexture, SkinModel.WIDE);
        try {
            SkinTextures overridden = SkinOverrideManager.apply(playerId, original);
            Identifier renderedTexture = overridden.body().texturePath();
            if (!registeredTexture.equals(renderedTexture)) {
                throw new AssertionError("Expected the registered texture " + registeredTexture
                        + " but the renderer received " + renderedTexture);
            }
        } finally {
            SkinOverrideManager.clear(playerId);
        }
    }
}
