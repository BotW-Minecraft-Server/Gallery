package link.botwmcs.gallery.entity;

import link.botwmcs.gallery.registration.ItemRegister;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class PaintingEntityGlowing extends PaintingEntity {
    public PaintingEntityGlowing(EntityType<? extends PaintingEntity> type, Level level) {
        super(type, level);
    }

    public boolean isGlowing() {
        return true;
    }

    public Item getItem() {
        return ItemRegister.GLOW_PAINTING_ITEM.get();
    }
}
