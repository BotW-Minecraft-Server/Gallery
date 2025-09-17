package link.botwmcs.gallery.network.s2c;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenPaintingScreenPayload(int entityId) implements CustomPacketPayload {
    public static final Type<OpenPaintingScreenPayload> TYPE = new Type<>(Gallery.locate("open_painting_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenPaintingScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenPaintingScreenPayload::entityId, OpenPaintingScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
