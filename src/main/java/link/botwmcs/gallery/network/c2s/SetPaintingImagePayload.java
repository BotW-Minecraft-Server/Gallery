package link.botwmcs.gallery.network.c2s;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetPaintingImagePayload(int entityId, ResourceLocation paintId) implements CustomPacketPayload {
    public static final Type<SetPaintingImagePayload> TYPE = new Type<>(Gallery.locate("set_painting_image"));
    public static final StreamCodec<FriendlyByteBuf, SetPaintingImagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetPaintingImagePayload::entityId,
                    ResourceLocation.STREAM_CODEC, SetPaintingImagePayload::paintId,
                    SetPaintingImagePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
