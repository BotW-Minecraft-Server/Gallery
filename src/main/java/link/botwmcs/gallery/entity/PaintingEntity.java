package link.botwmcs.gallery.entity;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.registration.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public class PaintingEntity extends HangingEntity {
    // 用于避免同向同类实体重叠的过滤器
    protected static final Predicate<Entity> SAME_CLASS = (e) -> e instanceof PaintingEntity;

    // 同步数据：资源外观（可选），以及逻辑尺寸
    private static final EntityDataAccessor<ResourceLocation> PAINT = SynchedEntityData.defineId(PaintingEntity.class, EntityRegister.TRACKED_IDENTIFIER);
    private static final EntityDataAccessor<ResourceLocation> FRAME = SynchedEntityData.defineId(PaintingEntity.class, EntityRegister.TRACKED_IDENTIFIER);
    private static final EntityDataAccessor<ResourceLocation> MATERIAL = SynchedEntityData.defineId(PaintingEntity.class, EntityRegister.TRACKED_IDENTIFIER);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(PaintingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(PaintingEntity.class, EntityDataSerializers.INT);

    // 地/顶放置时用于平面内旋转（度）
    private int rotation;

    public PaintingEntity(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    public void setDirection(Direction dir) {
        this.setDirection(dir, this.rotation);
    }

    public void setDirection(@Nullable Direction dir, int rotationDeg) {
        if (dir == null) return;
        this.direction = dir;
        this.rotation = Math.floorMod(rotationDeg, 360);

        if (dir.getAxis().isHorizontal()) {
            // 墙挂：朝向 → yaw（每 90°）
            this.absRotateTo(dir.get2DDataValue() * 90.0F, 0.0F);
        } else {
            // 地/顶：绕法线平面内转动 rotation，同时把 pitch 翻到 ±90°
            this.absRotateTo((float) this.rotation, dir == Direction.UP ? 90.0F : -90.0F);
        }
        this.recalculateBoundingBox();
    }

    public void setPos(BlockPos pos) {
        this.pos = pos; // HangingEntity 的锚点字段
    }

    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        this.setPos(x, y, z);
    }

    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }

    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction side) {
        Vec3 front = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 up = side.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 cross = up.cross(front);

        if (this.rotation != 0) {
            float rad = (float) (this.rotation * Math.PI / 180.0);
            up = up.yRot(rad);
            cross = cross.yRot(rad);
        }

        double dx = offsetForPaintingSize(getPaintingWidth());
        double dy = offsetForPaintingSize(getPaintingHeight());

        Direction facing;
        Direction counter;
        if (side.getAxis().isVertical()) {
            facing = Direction.fromYRot(this.rotation);
            if (side == Direction.UP) {
                facing = facing.getOpposite();
                counter = facing.getClockWise();
            } else {
                counter = facing.getCounterClockWise();
            }
        } else {
            facing = Direction.UP;
            counter = side.getCounterClockWise();
        }

        Vec3 center = Vec3.atCenterOf(pos)
                .relative(side, -0.46875F)
                .relative(counter, dx)
                .relative(facing, dy);

        Vec3 shift = up.scale(getPaintingHeight())
                .add(cross.scale(getPaintingWidth()))
                .add(front.scale(0.0625F));

        return AABB.ofSize(center, shift.x(), shift.y(), shift.z());
    }

    private static double offsetForPaintingSize(int size) {
        return (size % 2 == 0) ? 0.5D : 0.0D;
    }

    // ========= 存活 =========
    @Override
    public boolean survives() {
        BlockPos anchorBack = this.pos.relative(this.direction.getOpposite());
        BlockState behind = this.level().getBlockState(anchorBack);
        if (!behind.isSolid() && !DiodeBlock.isDiode(behind)) {
            return false;
        }
        return this.level().getEntities(this, this.getBoundingBox(), SAME_CLASS)
                .stream()
                .noneMatch(e -> ((PaintingEntity) e).direction == this.direction);
    }

    // ========= 交互：空壳 =========
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }





    // ========= 物品/音效 =========
    public Item getItem() {
        return Items.PAINTING;
    }

    public ItemStack getPickResult() {
        return new ItemStack(this.getItem());
    }



    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void dropItem(@Nullable Entity breaker) {
//        if (!this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) return;
//        this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
//        this.spawnAtLocation(this.getItem());

        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (breaker instanceof Player) {
                Player playerEntity = (Player)breaker;
                if (playerEntity.hasInfiniteMaterials()) {
                    return;
                }
            }

            this.spawnAtLocation(this.getItem());
        }

    }

    // ========= 同步数据 =========
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PAINT, Gallery.locate("none"));
        builder.define(FRAME, Gallery.locate("none"));
        builder.define(MATERIAL, Gallery.locate("none"));
        builder.define(WIDTH, 1);
        builder.define(HEIGHT, 1);
    }

    // ========= 网络 =========
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity tracker) {
//        int packed = (this.rotation & 0xFF) << 4 | (byte) this.direction.get3DDataValue();
//        return new ClientboundAddEntityPacket(this, packed, this.getPos());
        return new ClientboundAddEntityPacket(this, this.rotation << 4 | (byte)this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket pkt) {
//        super.recreateFromPacket(pkt);
//        int data = pkt.getData();
//        Direction dir = Direction.from3DDataValue(data & 15);
//        int rot = (data >> 4) & 0xFF;
//        this.setDirection(dir, rot);
        super.recreateFromPacket(pkt);
        int data = pkt.getData();
        this.setDirection(Direction.from3DDataValue(data & 15), data >> 4);
    }

    // ========= NBT =========
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Paint", getPaint().toString());
        tag.putString("Frame", getFrame().toString());
        tag.putString("Material", getMaterial().toString());
        tag.putInt("Facing", this.direction.get3DDataValue());
        tag.putInt("Rotation", this.rotation);
        tag.putInt("Width", getPaintingWidth());
        tag.putInt("Height", getPaintingHeight());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.direction = Direction.from3DDataValue(tag.getInt("Facing"));
        this.rotation  = tag.getInt("Rotation");
        super.readAdditionalSaveData(tag);

        this.setPaint(ResourceLocation.parse(tag.getString("Paint").isEmpty() ? "gallery:none" : tag.getString("Paint")));
        this.setFrame(ResourceLocation.parse(tag.getString("Frame").isEmpty() ? "gallery:none" : tag.getString("Frame")));
        this.setMaterial(ResourceLocation.parse(tag.getString("Material").isEmpty() ? "gallery:none" : tag.getString("Material")));

        this.setPaintingWidth(Math.max(1, tag.getInt("Width")));
        this.setPaintingHeight(Math.max(1, tag.getInt("Height")));
        this.recalculateBoundingBox();

    }

    // ========= Getter / Setter =========
    public ResourceLocation getPaint() { return this.getEntityData().get(PAINT); }
    public void setPaint(ResourceLocation id) { this.getEntityData().set(PAINT, id); }

    public ResourceLocation getFrame() { return this.getEntityData().get(FRAME); }
    public void setFrame(ResourceLocation id) { this.getEntityData().set(FRAME, id); }

    public ResourceLocation getMaterial() { return this.getEntityData().get(MATERIAL); }
    public void setMaterial(ResourceLocation id) { this.getEntityData().set(MATERIAL, id); }

    public int getPaintingWidth() { return this.getEntityData().get(WIDTH); }
    public void setPaintingWidth(int w) { this.getEntityData().set(WIDTH, Math.max(1, w)); }

    public int getPaintingHeight() { return this.getEntityData().get(HEIGHT); }
    public void setPaintingHeight(int h) { this.getEntityData().set(HEIGHT, Math.max(1, h)); }



}
