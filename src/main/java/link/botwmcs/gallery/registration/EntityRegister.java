package link.botwmcs.gallery.registration;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.entity.PaintingEntity;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.function.Supplier;

public final class EntityRegister {
    private static final ResourceLocation PAINTING_ID = Gallery.locate("painting");

    // 供 EntityData 同步使用的实例（需要在类加载时可用）
    public static final EntityDataSerializer<ResourceLocation> TRACKED_IDENTIFIER =
            EntityDataSerializer.forValueType(ResourceLocation.STREAM_CODEC);

    // 通过注册返回的 Supplier 取实体类型（避免初始化顺序问题）
    public static Supplier<EntityType<PaintingEntity>> PAINTING;

    public static void registerEntitySerializers(RegistryHelper<EntityDataSerializer<?>> helper) {
        // 注册“resource”序列化器；实例沿用上面的静态对象，保证你在 defineId 时可立即拿到
        helper.register(Gallery.locate("resource"), () -> TRACKED_IDENTIFIER);
    }

    public static void registerEntities(RegistryHelper<EntityType<?>> helper) {
        PAINTING = helper.register(PAINTING_ID,
                () -> EntityType.Builder.<PaintingEntity>of(PaintingEntity::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(10)
                        .updateInterval(Integer.MAX_VALUE)
                        .fireImmune()
                        .build(PAINTING_ID.toString()));
    }

    private EntityRegister() {}
}

