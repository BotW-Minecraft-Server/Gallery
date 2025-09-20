package link.botwmcs.gallery.network.s2c;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GetPaintingAutoFitPayload(int entityId, boolean autoFit) implements CustomPacketPayload {
    public static final Type<GetPaintingAutoFitPayload> TYPE = new Type<>(Gallery.locate("get_painting_autofit"));
    public static final StreamCodec<FriendlyByteBuf, GetPaintingAutoFitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GetPaintingAutoFitPayload::entityId,
                    ByteBufCodecs.BOOL, GetPaintingAutoFitPayload::autoFit,
                    GetPaintingAutoFitPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
