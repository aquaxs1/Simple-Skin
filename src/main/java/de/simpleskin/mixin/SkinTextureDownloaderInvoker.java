package de.simpleskin.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes Minecraft's own legacy-skin conversion so imported PNGs go through exactly the same
 * path as a downloaded one: 64x32 skins are expanded to 64x64 and the Notch transparency hack is
 * applied.
 */
@Mixin(SkinTextureDownloader.class)
public interface SkinTextureDownloaderInvoker {
    @Invoker("processLegacySkin")
    static NativeImage simpleSkin$processLegacySkin(NativeImage image, String source) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
