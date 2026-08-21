package de.simpleskin;

import de.simpleskin.data.SkinModel;
import de.simpleskin.skin.MinecraftSkinUploadService;
import de.simpleskin.skin.SkinOverrideManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs without a game instance: every case below only touches plain records and pure helpers.
 */
public final class SimpleSkinRegressionTest {
    private static final UUID PLAYER = UUID.fromString("7aa56f75-86a4-4bfe-9d9d-801c03e9d8e1");
    private static final Identifier SKIN_TEXTURE = Identifier.fromNamespaceAndPath("simple_skin", "skins/regression");

    private SimpleSkinRegressionTest() {
    }

    public static void main(String[] args) {
        equippedSkinPointsAtRegisteredTexture();
        overrideKeepsCapeElytraAndSecureFlag();
        overrideIsNotRebuiltForUnchangedTextures();
        slimModelSurvivesTheOverride();
        activeProfileSkinUrlIsParsed();
        inactiveProfileSkinIsIgnored();
        System.out.println("Simple Skin regression checks passed.");
    }

    /** The renderer must receive the texture the mod registered, not the profile's skin. */
    private static void equippedSkinPointsAtRegisteredTexture() {
        withOverride(SkinModel.WIDE, () -> {
            PlayerSkin result = SkinOverrideManager.apply(PLAYER, original(true));
            assertEquals(SKIN_TEXTURE, result.body().texturePath(), "rendered body texture");
        });
    }

    /**
     * Regression: the override used to rebuild the skin from scratch with secure=false, which
     * stripped the player's cape and elytra the moment a skin was equipped. Minecraft's own
     * {@code PlayerSkin#with} still does exactly that, so this guards the hand-rolled copy.
     */
    private static void overrideKeepsCapeElytraAndSecureFlag() {
        withOverride(SkinModel.WIDE, () -> {
            PlayerSkin source = original(true);
            PlayerSkin result = SkinOverrideManager.apply(PLAYER, source);
            assertEquals(source.cape(), result.cape(), "cape");
            assertEquals(source.elytra(), result.elytra(), "elytra");
            assertEquals(true, result.secure(), "secure flag");
        });
    }

    /**
     * Regression: apply() runs from getSkin(), i.e. per player per frame, so equal inputs must
     * hand back the memoised instance instead of allocating a new one every time.
     */
    private static void overrideIsNotRebuiltForUnchangedTextures() {
        withOverride(SkinModel.WIDE, () -> {
            PlayerSkin source = original(true);
            PlayerSkin first = SkinOverrideManager.apply(PLAYER, source);
            PlayerSkin second = SkinOverrideManager.apply(PLAYER, source);
            if (first != second) {
                throw new AssertionError("apply() allocated a new PlayerSkin for unchanged input");
            }
        });
    }

    private static void slimModelSurvivesTheOverride() {
        withOverride(SkinModel.SLIM, () -> {
            PlayerSkin result = SkinOverrideManager.apply(PLAYER, original(true));
            assertEquals(PlayerModelType.SLIM, result.model(), "skin model");
        });
    }

    private static void activeProfileSkinUrlIsParsed() {
        String json = """
                {"id":"abc","name":"Steve","skins":[
                  {"id":"1","state":"INACTIVE","url":"https://textures.minecraft.net/texture/old","variant":"CLASSIC"},
                  {"id":"2","state":"ACTIVE","url":"https://textures.minecraft.net/texture/new","variant":"SLIM"}]}""";
        assertEquals(Optional.of(URI.create("https://textures.minecraft.net/texture/new")),
                MinecraftSkinUploadService.parseActiveSkinUrl(json), "active skin url");
    }

    private static void inactiveProfileSkinIsIgnored() {
        String json = "{\"skins\":[{\"state\":\"INACTIVE\",\"url\":\"https://textures.minecraft.net/texture/old\"}]}";
        assertEquals(Optional.empty(), MinecraftSkinUploadService.parseActiveSkinUrl(json), "no active skin");
        assertEquals(Optional.empty(), MinecraftSkinUploadService.parseActiveSkinUrl("not json"), "malformed body");
    }

    private static PlayerSkin original(boolean secure) {
        return new PlayerSkin(
                new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("minecraft", "entity/player/wide/steve")),
                new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("minecraft", "entity/cape/regression")),
                new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("minecraft", "entity/elytra/regression")),
                PlayerModelType.WIDE,
                secure);
    }

    private static void withOverride(SkinModel model, Runnable body) {
        SkinOverrideManager.set(PLAYER, SKIN_TEXTURE, model);
        try {
            body.run();
        } finally {
            SkinOverrideManager.clear(PLAYER);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + what + " to be " + expected + " but it was " + actual);
        }
    }
}
