package link.botwmcs.gallery.network.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GetPaintingSizePayload(int entityId, int width, int height) implements CustomPacketPayload {
    public static final Type<GetPaintingSizePayload> TYPE = new Type<>(link.botwmcs.gallery.Gallery.locate("get_painting_size"));
    public static final StreamCodec<FriendlyByteBuf, GetPaintingSizePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GetPaintingSizePayload::entityId,
                    ByteBufCodecs.VAR_INT, GetPaintingSizePayload::width,
                    ByteBufCodecs.VAR_INT, GetPaintingSizePayload::height,
                    GetPaintingSizePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
