package de.simpleskin.net;

import de.simpleskin.SimpleSkinClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * The wire format for live skin sync.
 *
 * <p>This is what lets other players see a skin change immediately, with no rejoin — but only on
 * a server that also runs Simple Skin. On a vanilla server the channel is simply never
 * negotiated, {@code canSend} returns false, and the mod falls back to the upload-and-rejoin
 * route. Nothing is ever sent to a server that did not advertise the channel.
 */
public final class SkinSyncPayloads {
    /** A 64x64 skin PNG is a few kilobytes; this cap keeps a hostile peer from sending more. */
    public static final int MAX_PNG_BYTES = 64 * 1024;

    public static final CustomPacketPayload.Type<Equip> EQUIP_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimpleSkinClient.MOD_ID, "equip"));
    public static final CustomPacketPayload.Type<Wearing> WEARING_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimpleSkinClient.MOD_ID, "wearing"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, byte[]> PNG =
            ByteBufCodecs.byteArray(MAX_PNG_BYTES);

    /** Client to server: "this is the skin I just put on." */
    public static final StreamCodec<RegistryFriendlyByteBuf, Equip> EQUIP_CODEC = StreamCodec.composite(
            PNG, Equip::png,
            ByteBufCodecs.BOOL, Equip::slim,
            Equip::new);

    /** Server to client: "this player is wearing this skin." */
    public static final StreamCodec<RegistryFriendlyByteBuf, Wearing> WEARING_CODEC = StreamCodec.composite(
            net.minecraft.core.UUIDUtil.STREAM_CODEC, Wearing::player,
            PNG, Wearing::png,
            ByteBufCodecs.BOOL, Wearing::slim,
            Wearing::new);

    private SkinSyncPayloads() {
    }

    public record Equip(byte[] png, boolean slim) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return EQUIP_TYPE;
        }
    }

    public record Wearing(UUID player, byte[] png, boolean slim) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return WEARING_TYPE;
        }
    }
}
