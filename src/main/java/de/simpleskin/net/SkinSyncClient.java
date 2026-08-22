package de.simpleskin.net;

import com.mojang.blaze3d.platform.NativeImage;
import de.simpleskin.data.SkinModel;
import de.simpleskin.data.SkinRepository;
import de.simpleskin.mixin.SkinTextureDownloaderInvoker;
import de.simpleskin.skin.SkinOverrideManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client half of live skin sync: publishes the skin this player equips and applies the skins
 * other Simple Skin players are wearing, with no rejoin involved.
 */
public final class SkinSyncClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");

    /** Textures registered for remote players, so they can be released on disconnect. */
    private final Map<UUID, Identifier> remoteTextures = new HashMap<>();

    public void register() {
        SimpleSkinServer.SkinSyncRegistry.registerTypes();
        ClientPlayNetworking.registerGlobalReceiver(SkinSyncPayloads.WEARING_TYPE,
                (payload, context) -> context.client().execute(() -> accept(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
    }

    /** True when the current server speaks Simple Skin's channel. */
    public boolean available() {
        return ClientPlayNetworking.canSend(SkinSyncPayloads.EQUIP_TYPE);
    }

    /**
     * Tells the server which skin this player put on. Does nothing on a server without the mod,
     * where the upload-and-rejoin route is the only option.
     */
    public boolean publish(byte[] png, SkinModel model) {
        if (!available() || png.length > SkinSyncPayloads.MAX_PNG_BYTES) {
            return false;
        }
        ClientPlayNetworking.send(new SkinSyncPayloads.Equip(png, model == SkinModel.SLIM));
        return true;
    }

    private void accept(SkinSyncPayloads.Wearing message) {
        try {
            // The sender is another player: never trust the bytes, decode them as a skin or drop them.
            SkinRepository.validatePng(message.png());
        } catch (IOException exception) {
            LOGGER.warn("Ignoring a malformed synced skin for {}", message.player());
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Identifier identifier = Identifier.fromNamespaceAndPath("simple_skin",
                "synced/" + message.player().toString().replace("-", ""));
        try (ByteArrayInputStream input = new ByteArrayInputStream(message.png())) {
            NativeImage image = NativeImage.read(input);
            image = SkinTextureDownloaderInvoker.simpleSkin$processLegacySkin(image, "synced skin");
            client.getTextureManager().register(identifier, new DynamicTexture(() -> "Simple Skin: synced", image));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not decode a synced skin for {}", message.player(), exception);
            return;
        }
        remoteTextures.put(message.player(), identifier);
        SkinOverrideManager.set(message.player(), identifier,
                message.slim() ? SkinModel.SLIM : SkinModel.WIDE);
    }

    /** Drops every synced override and its texture when leaving the server. */
    private void clearAll() {
        Minecraft client = Minecraft.getInstance();
        remoteTextures.forEach((player, identifier) -> {
            SkinOverrideManager.clear(player);
            client.getTextureManager().release(identifier);
        });
        remoteTextures.clear();
    }
}
