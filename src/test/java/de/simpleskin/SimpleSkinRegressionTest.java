package de.simpleskin;

import de.simpleskin.data.SkinModel;
import de.simpleskin.skin.MinecraftSkinUploadService;
import de.simpleskin.skin.MojangProfileService;
import de.simpleskin.skin.SkinEditor;
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
        capeOverrideHidesAndReplaces();
        ownedCapesAreParsed();
        playerNamesAreValidated();
        mojangProfileIdBecomesUuid();
        editorClearsOnlyTheChosenLayer();
        editorSwapsTheHeadOnly();
        editorReplacesColourKeepingAlpha();
        legacySkinsAreExpandedTo64x64();
        System.out.println("Simple Skin regression checks passed.");
    }

    /** An outfit that hides the cape must clear the slot, and one that picks a cape must fill it. */
    private static void capeOverrideHidesAndReplaces() {
        Identifier cape = Identifier.fromNamespaceAndPath("simple_skin", "capes/regression");
        SkinOverrideManager.set(PLAYER, SKIN_TEXTURE, SkinModel.WIDE,
                SkinOverrideManager.CapeOverride.HIDE, null);
        try {
            assertEquals(null, SkinOverrideManager.apply(PLAYER, original(true)).cape(), "hidden cape");
        } finally {
            SkinOverrideManager.clear(PLAYER);
        }
        SkinOverrideManager.set(PLAYER, SKIN_TEXTURE, SkinModel.WIDE,
                SkinOverrideManager.CapeOverride.REPLACE, cape);
        try {
            PlayerSkin result = SkinOverrideManager.apply(PLAYER, original(true));
            assertEquals(cape, result.cape().texturePath(), "replaced cape");
            assertEquals(SKIN_TEXTURE, result.body().texturePath(), "body kept while replacing the cape");
        } finally {
            SkinOverrideManager.clear(PLAYER);
        }
    }

    private static void ownedCapesAreParsed() {
        String json = """
                {"capes":[
                  {"id":"c1","state":"INACTIVE","url":"https://textures.minecraft.net/texture/a","alias":"Migrator"},
                  {"id":"c2","state":"ACTIVE","url":"https://textures.minecraft.net/texture/b","alias":"Vanilla"}]}""";
        var capes = MinecraftSkinUploadService.parseCapes(json);
        assertEquals(2, capes.size(), "cape count");
        assertEquals("Migrator", capes.get(0).alias(), "first cape alias");
        assertEquals(true, capes.get(1).active(), "second cape active");
        assertEquals(0, MinecraftSkinUploadService.parseCapes("nonsense").size(), "malformed cape body");
    }

    private static void playerNamesAreValidated() {
        assertEquals(true, MojangProfileService.isValidName("Notch"), "plain name");
        assertEquals(true, MojangProfileService.isValidName("a_1"), "short name with underscore");
        assertEquals(false, MojangProfileService.isValidName("no"), "too short");
        assertEquals(false, MojangProfileService.isValidName("seventeen_chars__"), "too long");
        assertEquals(false, MojangProfileService.isValidName("bad name"), "space is rejected");
        assertEquals(false, MojangProfileService.isValidName("drop;table"), "punctuation is rejected");
    }

    private static void mojangProfileIdBecomesUuid() {
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                MojangProfileService.toUuid("069a79f444e94726a5befca90e38aaf5", "Notch"), "dash-less uuid");
    }

    /** Removing the jacket must not touch the hat, and vice versa. */
    private static void editorClearsOnlyTheChosenLayer() throws RuntimeException {
        try {
            byte[] skin = solidSkin(0xFF3366CC);
            byte[] noJacket = SkinEditor.clearLayer(skin, SkinEditor.Layer.JACKET);
            assertEquals(false, SkinEditor.hasLayer(noJacket, SkinEditor.Layer.JACKET), "jacket cleared");
            assertEquals(true, SkinEditor.hasLayer(noJacket, SkinEditor.Layer.HAT), "hat untouched");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void editorSwapsTheHeadOnly() {
        try {
            byte[] base = solidSkin(0xFF112233);
            byte[] donor = solidSkin(0xFFAABBCC);
            byte[] swapped = SkinEditor.swapHead(base, donor);
            // The head block is the top 16 rows; the body below it must survive untouched.
            assertEquals(0xFFAABBCC, SkinEditor.pixel(swapped, 8, 8), "head pixel from the donor");
            assertEquals(0xFF112233, SkinEditor.pixel(swapped, 20, 20), "body pixel from the base");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void editorReplacesColourKeepingAlpha() {
        try {
            byte[] skin = solidSkin(0xFF3366CC);
            byte[] recoloured = SkinEditor.replaceColour(skin, 0xFF3366CC, 0xFFCC6633, 0);
            int pixel = SkinEditor.pixel(recoloured, 20, 20);
            assertEquals(0xFFCC6633, pixel, "replaced colour");
            assertEquals(0xFF, pixel >>> 24, "alpha preserved");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    /** A 64x32 skin must come back as 64x64 with the legacy limbs mirrored into place. */
    private static void legacySkinsAreExpandedTo64x64() {
        try {
            java.awt.image.BufferedImage legacy =
                    new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 32; y++) {
                    legacy.setRGB(x, y, 0xFF445566);
                }
            }
            byte[] png = SkinEditor.write(legacy);
            // The left leg slot only exists in the 64x64 layout, so a filled pixel there proves
            // the legacy image was expanded rather than left half-height.
            assertEquals(0xFF445566, SkinEditor.pixel(png, 20, 52), "mirrored left leg");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    /** A 64x64 skin filled with one opaque colour, used as editor input. */
    private static byte[] solidSkin(int argb) throws java.io.IOException {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                image.setRGB(x, y, argb);
            }
        }
        return SkinEditor.write(image);
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
