package link.botwmcs.gallery.item;

import link.botwmcs.gallery.entity.PaintingEntity;
import link.botwmcs.gallery.registration.EntityRegister;
import net.minecraft.world.entity.EntityType;

public class PaintingGlowItem extends PaintingItem {
    protected EntityType<? extends PaintingEntity> getEntityType() {
        return EntityRegister.GLOW_PAINTING.get();
    }
}
