package link.botwmcs.gallery.registration;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.entity.PaintingEntity;

import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class Entities {
    private static final ResourceLocation PAINTING_LOCATION = Gallery.locate("painting");
    private static final ResourceLocation GLOW_PAINTING_LOCATION = Gallery.locate("glow_painting");
    private static final ResourceLocation GRAFFITI_LOCATION = Gallery.locate("graffiti");
    private static final ResourceLocation GLOW_GRAFFITI_LOCATION = Gallery.locate("glow_graffiti");
    public static final EntityType<PaintingEntity> PAINTING;
//    public static final EntityType<ImmersiveGlowPaintingEntity> GLOW_PAINTING;
//    public static final EntityType<ImmersiveGraffitiEntity> GRAFFITI;
//    public static final EntityType<ImmersiveGlowGraffitiEntity> GLOW_GRAFFITI;
    public static final EntityDataSerializer<ResourceLocation> TRACKED_IDENTIFIER;

    private static <T extends PaintingEntity> EntityType<T> createEntityType(EntityType.EntityFactory<T> factory, ResourceLocation name) {
        return EntityType.Builder.of(factory, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE).fireImmune().build(name.toString());
    }

    public static void registerEntities(GalleryRegisterHelper<EntityType<?>> helper) {
        helper.register(PAINTING_LOCATION, PAINTING);
//        helper.register(GLOW_PAINTING_LOCATION, GLOW_PAINTING);
//        helper.register(GRAFFITI_LOCATION, GRAFFITI);
//        helper.register(GLOW_GRAFFITI_LOCATION, GLOW_GRAFFITI);
    }

    public static void registerEntitySerializers(GalleryRegisterHelper<EntityDataSerializer<?>> helper) {
        helper.register(Gallery.locate("resource"), TRACKED_IDENTIFIER);
    }

    static {
        PAINTING = createEntityType(PaintingEntity::new, PAINTING_LOCATION);
//        GLOW_PAINTING = createEntityType(ImmersiveGlowPaintingEntity::new, GLOW_PAINTING_LOCATION);
//        GRAFFITI = createEntityType(ImmersiveGraffitiEntity::new, GRAFFITI_LOCATION);
//        GLOW_GRAFFITI = createEntityType(ImmersiveGlowGraffitiEntity::new, GLOW_GRAFFITI_LOCATION);
        TRACKED_IDENTIFIER = EntityDataSerializer.forValueType(ResourceLocation.STREAM_CODEC);
    }

}
