package link.botwmcs.gallery.registration;

import link.botwmcs.gallery.client.render.PaintingEntityRenderer;
import link.botwmcs.gallery.entity.PaintingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;

public final class RendererRegister {
    private RendererRegister() {}
    public static void register(Handler handler) {
        handler.handle(EntityRegister.PAINTING.get(), PaintingEntityRenderer::new);
        handler.handle(EntityRegister.GLOW_PAINTING.get(), PaintingEntityRenderer::new);
    }

    public interface Handler {
        <T extends PaintingEntity> void handle(EntityType<T> type, EntityRendererProvider<T> factory);
    }
}
