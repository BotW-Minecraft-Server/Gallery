package link.botwmcs.gallery.network.s2c;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GetFramePayload(int entityId, ResourceLocation frameId) implements CustomPacketPayload {
    public static final Type<GetFramePayload> TYPE = new Type<>(Gallery.locate("get_frame"));
    public static final StreamCodec<FriendlyByteBuf, GetFramePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GetFramePayload::entityId,
                    ResourceLocation.STREAM_CODEC, GetFramePayload::frameId,
                    GetFramePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
