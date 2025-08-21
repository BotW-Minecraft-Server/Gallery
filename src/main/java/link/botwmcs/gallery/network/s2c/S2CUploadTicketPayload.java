package link.botwmcs.gallery.network.s2c;

import io.netty.buffer.ByteBuf;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.identify.Painting;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CUploadTicketPayload(String uploadUrl, String ticketId, long expiresAtMs) implements CustomPacketPayload {
    public static final Type<S2CUploadTicketPayload> TYPE =
            new Type<>(Gallery.locate("s2c_upload_ticket"));

    public static final StreamCodec<ByteBuf, S2CUploadTicketPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, S2CUploadTicketPayload::uploadUrl,
                    ByteBufCodecs.STRING_UTF8, S2CUploadTicketPayload::ticketId,
                    ByteBufCodecs.VAR_LONG,   S2CUploadTicketPayload::expiresAtMs,
                    S2CUploadTicketPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
