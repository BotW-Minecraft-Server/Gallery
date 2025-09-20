package link.botwmcs.gallery.network.s2c;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GetPaintingImagePayload(int entityId, ResourceLocation paintId) implements CustomPacketPayload {
    public static final Type<GetPaintingImagePayload> TYPE = new Type<>(Gallery.locate("get_painting_image"));
    public static final StreamCodec<FriendlyByteBuf, GetPaintingImagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GetPaintingImagePayload::entityId,
                    ResourceLocation.STREAM_CODEC, GetPaintingImagePayload::paintId,
                    GetPaintingImagePayload::new
            );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
