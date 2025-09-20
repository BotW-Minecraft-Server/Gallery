package link.botwmcs.gallery.network.c2s;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetPaintingAutoFitPayload(int entityId, boolean autoFit) implements CustomPacketPayload {
    public static final Type<SetPaintingAutoFitPayload> TYPE = new Type<>(Gallery.locate("set_autofit"));
    public static final StreamCodec<FriendlyByteBuf, SetPaintingAutoFitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetPaintingAutoFitPayload::entityId,
                    ByteBufCodecs.BOOL, SetPaintingAutoFitPayload::autoFit,
                    SetPaintingAutoFitPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
