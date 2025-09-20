package link.botwmcs.gallery.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetPaintingSizePayload(int entityId, int width, int height) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<SetPaintingSizePayload> TYPE = new Type<>(link.botwmcs.gallery.Gallery.locate("set_painting_size"));
    public static final StreamCodec<FriendlyByteBuf, SetPaintingSizePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetPaintingSizePayload::entityId,
                    ByteBufCodecs.VAR_INT, SetPaintingSizePayload::width,
                    ByteBufCodecs.VAR_INT, SetPaintingSizePayload::height,
                    SetPaintingSizePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
