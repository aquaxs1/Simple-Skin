package de.simpleskin.net;

import de.simpleskin.data.SkinRepository;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server half of live skin sync: it relays a player's equipped skin to every other Simple
 * Skin client, so a swap is visible immediately instead of only after a rejoin.
 *
 * <p>Installing the mod on the server is optional. Without it clients simply never negotiate the
 * channel and fall back to uploading to the Mojang profile and rejoining.
 */
public final class SimpleSkinServer implements ModInitializer, DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");

    /** Last skin each player published, so someone joining late still sees everyone correctly. */
    private static final Map<UUID, SkinSyncPayloads.Wearing> WORN = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        SkinSyncRegistry.registerTypes();
        registerReceiver();
    }

    @Override
    public void onInitializeServer() {
        // Both entrypoints run onInitialize on a dedicated server; nothing extra is needed here.
    }

    private void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(SkinSyncPayloads.EQUIP_TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            byte[] png = payload.png();
            try {
                // The relay re-validates: a client could send anything, and every other client is
                // about to decode this as a texture.
                SkinRepository.validatePng(png);
            } catch (IOException exception) {
                LOGGER.warn("Simple Skin rejected a skin from {}: {}", sender.getGameProfile().name(),
                        exception.getMessage());
                return;
            }
            SkinSyncPayloads.Wearing message =
                    new SkinSyncPayloads.Wearing(sender.getUUID(), png, payload.slim());
            WORN.put(sender.getUUID(), message);
            broadcast(context.server(), sender, message);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> catchUp(handler.getPlayer()));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> WORN.remove(handler.getPlayer().getUUID()));
    }

    /** Sends every known skin to a player who just joined. */
    private static void catchUp(ServerPlayer joined) {
        if (!ServerPlayNetworking.canSend(joined, SkinSyncPayloads.WEARING_TYPE)) {
            return;
        }
        for (SkinSyncPayloads.Wearing message : WORN.values()) {
            if (!message.player().equals(joined.getUUID())) {
                ServerPlayNetworking.send(joined, message);
            }
        }
    }

    private static void broadcast(net.minecraft.server.MinecraftServer server, ServerPlayer sender,
            SkinSyncPayloads.Wearing message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(sender.getUUID())) {
                continue;
            }
            if (ServerPlayNetworking.canSend(player, SkinSyncPayloads.WEARING_TYPE)) {
                ServerPlayNetworking.send(player, message);
            }
        }
    }

    /** Registers the payload types exactly once, from whichever side initialises first. */
    static final class SkinSyncRegistry {
        private static boolean registered;

        private SkinSyncRegistry() {
        }

        static synchronized void registerTypes() {
            if (registered) {
                return;
            }
            registered = true;
            PayloadTypeRegistry.clientboundPlay().register(SkinSyncPayloads.WEARING_TYPE, SkinSyncPayloads.WEARING_CODEC);
            PayloadTypeRegistry.serverboundPlay().register(SkinSyncPayloads.EQUIP_TYPE, SkinSyncPayloads.EQUIP_CODEC);
        }
    }
}
