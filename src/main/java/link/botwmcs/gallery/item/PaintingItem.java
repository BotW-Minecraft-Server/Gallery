package link.botwmcs.gallery.item;

import link.botwmcs.gallery.entity.PaintingEntity;
import link.botwmcs.gallery.registration.EntityRegister;
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

    /** 可根据需要覆写权限判断（默认沿用 vanilla 行为） */
    protected boolean mayUseItemAt(Player player, Direction side, ItemStack stack, BlockPos pos) {
        return player.mayUseItemAt(pos, side, stack);
    }

    protected EntityType<? extends PaintingEntity> getEntityType() {
        return EntityRegister.PAINTING.get();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        BlockPos attachPos = clicked.relative(face);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (player == null || !this.mayUseItemAt(player, face, stack, attachPos)) {
            return InteractionResult.FAIL;
        }

        Level level = ctx.getLevel();
        int rotation = 0;
        if (face.getAxis().isVertical()) {
            rotation = Math.floorMod((int)Math.floor((player.getYRot() / 90.0F) + 2.5F) * 90, 360);
        }

        PaintingEntity entity = this.getEntityType().create(level);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        entity.setPos(attachPos);
        entity.setDirection(face, rotation);

        if (!entity.survives()) {
            return InteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            entity.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
            level.addFreshEntity(entity);
        }

        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);

    }

}
