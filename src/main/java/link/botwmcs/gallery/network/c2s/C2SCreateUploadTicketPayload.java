package link.botwmcs.gallery.network.c2s;

import io.netty.buffer.ByteBuf;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.identify.Painting;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SCreateUploadTicketPayload(Painting meta, String sha256Hex, String ext, long sizeBytes) implements CustomPacketPayload {
    public static final Type<C2SCreateUploadTicketPayload> TYPE =
            new Type<>(Gallery.locate("c2s_upload_ticket"));
    public static final StreamCodec<ByteBuf, C2SCreateUploadTicketPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Painting.STREAM_CODEC, C2SCreateUploadTicketPayload::meta,
                    ByteBufCodecs.STRING_UTF8, C2SCreateUploadTicketPayload::sha256Hex,
                    ByteBufCodecs.STRING_UTF8, C2SCreateUploadTicketPayload::ext,
                    ByteBufCodecs.VAR_LONG, C2SCreateUploadTicketPayload::sizeBytes,
                    C2SCreateUploadTicketPayload::new
            );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
