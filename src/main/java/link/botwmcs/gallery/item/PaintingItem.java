package link.botwmcs.gallery.item;

import link.botwmcs.gallery.entity.PaintingEntity;
import link.botwmcs.gallery.registration.Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class PaintingItem extends Item {
    public PaintingItem() {
        super(new Item.Properties());
    }

    public PaintingItem(Properties properties) {
        super(properties);
    }

    protected boolean mayUseItemAt(Player player, Direction side, ItemStack stack, BlockPos pos) {
        return player.mayUseItemAt(pos, side, stack);
    }

    protected EntityType<? extends PaintingEntity> getEntityType() {
        return Entities.PAINTING;
    }

    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        BlockPos attachmentPosition = blockPos.relative(direction);
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();
        if (player != null && this.mayUseItemAt(player, direction, itemStack, attachmentPosition)) {
            Level level = context.getLevel();
            int rotation = 0;
            if (direction.getAxis().isVertical()) {
                rotation = Math.floorMod((int)Math.floor((double)(player.getYRot() / 90.0F) + (double)2.5F) * 90, 360);
            }

            PaintingEntity entity = (PaintingEntity)this.getEntityType().create(level);
            if (entity == null) {
                return InteractionResult.FAIL;
            } else {
                entity.setPos(attachmentPosition);
                entity.setDirection(direction, rotation);
                if (entity.survives()) {
                    if (!level.isClientSide) {
                        entity.playPlacementSound();
                        level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
                        level.addFreshEntity(entity);
                    }

                    itemStack.shrink(1);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else {
                    return InteractionResult.CONSUME;
                }
            }
        } else {
            return InteractionResult.FAIL;
        }
    }
}
