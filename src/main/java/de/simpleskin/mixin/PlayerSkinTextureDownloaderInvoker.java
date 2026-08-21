package de.simpleskin.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerSkinTextureDownloader.class)
public interface PlayerSkinTextureDownloaderInvoker {
    @Invoker("remapTexture")
    static NativeImage simpleSkin$remapTexture(NativeImage image, String source) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
