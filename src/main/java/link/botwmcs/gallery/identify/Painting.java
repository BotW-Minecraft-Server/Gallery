package link.botwmcs.gallery.identify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import link.botwmcs.gallery.Gallery;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.UUID;

public record Painting(int version,
                       int width,
                       int height,
                       int resolution,
                       String name,
                       String author,
                       UUID authorUUID,
                       EnumSet<Flag> flags,
                       String hash
) {
    public static final Codec<Painting> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("version").forGetter(Painting::version),
            Codec.INT.fieldOf("width").forGetter(Painting::width),
            Codec.INT.fieldOf("height").forGetter(Painting::height),
            Codec.INT.fieldOf("resolution").forGetter(Painting::resolution),
            Codec.STRING.fieldOf("name").forGetter(Painting::name),
            Codec.STRING.fieldOf("author").forGetter(Painting::author),
            UUIDUtil.CODEC.fieldOf("authorUUID").forGetter(Painting::authorUUID),
            Flag.CODEC.fieldOf("flags").forGetter(Painting::flags),
            Codec.STRING.fieldOf("hash").forGetter(Painting::hash)
    ).apply(i, Painting::new));

    public static final StreamCodec<ByteBuf, Painting> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    public static final ResourceLocation DEFAULT_IDENTIFIER = Gallery.locate("textures/block/frame/canvas.png");

    // 简化的构造器（默认 version = 1）
    public Painting(int width, int height, int resolution,
                    String name, String author, UUID authorUUID,
                    EnumSet<Flag> flags, String hash) {
        this(1, width, height, resolution, name, author, authorUUID, flags, hash);
    }

    // 直接生成 ResourceLocation（只保留 PAINTING）
    public ResourceLocation location() {
        return Gallery.locate(authorUUID.toString() + "/" + hash);
    }

    public boolean has(Flag flag) {
        return this.flags().contains(flag);
    }

    public Painting withHash(String newHash) {
        return new Painting(width, height, resolution, name, author, authorUUID, flags, newHash);
    }

    public enum Flag {
        HIDDEN(1L),
        NSFW(2L),
        GRAFFITI(4L);

        private final long bits;

        public static final PrimitiveCodec<EnumSet<Flag>> CODEC = new PrimitiveCodec<>() {
            public <T> DataResult<EnumSet<Flag>> read(DynamicOps<T> ops, T input) {
                DataResult<Long> result = ops.getNumberValue(input).map(Number::longValue);
                if (result.isError()) return DataResult.error(() -> "No long provided for reading FlagSet");
                long l = result.getOrThrow();
                EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
                for (Flag f : Flag.values()) if ((l & f.bits) != 0L) flags.add(f);
                return DataResult.success(flags);
            }

            public <T> T write(DynamicOps<T> ops, EnumSet<Flag> value) {
                long l = value.stream().map(f -> f.bits).reduce(0L, (a, b) -> a | b);
                return ops.createLong(l);
            }

            public String toString() {
                return "Flags";
            }
        };

        public static final StreamCodec<ByteBuf, EnumSet<Flag>> STREAM_CODEC =
                ByteBufCodecs.fromCodec(CODEC);

        Flag(long bits) {
            this.bits = bits;
        }
    }
}
