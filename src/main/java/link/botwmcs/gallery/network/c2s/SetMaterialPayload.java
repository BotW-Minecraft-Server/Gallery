package link.botwmcs.gallery.network.c2s;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetMaterialPayload(int entityId, ResourceLocation materialId) implements CustomPacketPayload {
    public static final Type<SetMaterialPayload> TYPE = new Type<>(Gallery.locate("set_material"));
    public static final StreamCodec<FriendlyByteBuf, SetMaterialPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetMaterialPayload::entityId,
                    ResourceLocation.STREAM_CODEC, SetMaterialPayload::materialId,
                    SetMaterialPayload::new
            );



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
