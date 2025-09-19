package link.botwmcs.gallery.network.c2s;

import link.botwmcs.gallery.Gallery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFramePayload(int entityId, ResourceLocation frameId) implements CustomPacketPayload {
    public static final Type<SetFramePayload> TYPE = new Type<>(Gallery.locate("set_frame"));
    public static final StreamCodec<FriendlyByteBuf, SetFramePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetFramePayload::entityId,
                    ResourceLocation.STREAM_CODEC, SetFramePayload::frameId,
                    SetFramePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
