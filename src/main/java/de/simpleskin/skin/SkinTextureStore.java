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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinTextureStore {
    private final SkinRepository repository;
    private final Map<String, Identifier> textures = new ConcurrentHashMap<>();

    public SkinTextureStore(SkinRepository repository) {
        this.repository = repository;
    }

    public Identifier getOrLoad(StoredSkin skin) throws IOException {
        Identifier cached = textures.get(skin.id());
        if (cached != null) {
            return cached;
        }
        Identifier identifier = Identifier.of("simple_skin", "skins/" + skin.id().replace("-", ""));
        try (InputStream input = Files.newInputStream(repository.imagePath(skin))) {
            NativeImage image = NativeImage.read(input);
            image = PlayerSkinTextureDownloaderInvoker.simpleSkin$remapTexture(image, skin.name());
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Simple Skin: " + skin.name(), image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
        }
        textures.put(skin.id(), identifier);
        return identifier;
    }
}
