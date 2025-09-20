package link.botwmcs.gallery.network.s2c;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GetMaterialPayload(int entityId, ResourceLocation materialId) implements CustomPacketPayload {
    public static final Type<GetMaterialPayload> TYPE = new Type<>(Gallery.locate("get_material"));
    public static final StreamCodec<FriendlyByteBuf, GetMaterialPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GetMaterialPayload::entityId,
                    ResourceLocation.STREAM_CODEC, GetMaterialPayload::materialId,
                    GetMaterialPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
