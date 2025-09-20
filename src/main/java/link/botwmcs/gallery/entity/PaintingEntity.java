package link.botwmcs.gallery.entity;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.item.PaintingItem;
import link.botwmcs.gallery.network.s2c.*;
import link.botwmcs.gallery.registration.EntityRegister;
import link.botwmcs.gallery.registration.ItemRegister;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

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
    private static final EntityDataAccessor<Boolean> AUTO_FIT = SynchedEntityData.defineId(PaintingEntity.class, EntityDataSerializers.BOOLEAN);

    // 地/顶放置时用于平面内旋转（度）
    private int rotation;
    private static final double CANVAS_THICKNESS = 0.0625D;               // 1/16 方块厚
    private static final double FACE_OFFSET = 0.5D - CANVAS_THICKNESS/2;  // 0.46875，与渲染保持一致避免Z-fight

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
        // 这三轴仍沿世界轴，数值只有 -1/0/1
        Vec3 front = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 up    = side.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 cross = up.cross(front);

        // 地/顶时允许在平面内按 rotation 旋转
        if (this.rotation != 0) {
            float rad = (float)(this.rotation * Math.PI / 180.0);
            up    = up.yRot(rad);
            cross = cross.yRot(rad);
        }

        // 使偶数尺寸能落在方块网格中心
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

        // 中心点：从锚点中心，沿法线拉出一点避免贴墙 z-fight，并在平面内按尺寸微调
        Vec3 center = Vec3.atCenterOf(pos)
                .relative(side, -FACE_OFFSET)
                .relative(counter, dx)
                .relative(facing, dy);

        // 三轴尺寸：宽/高/厚映射到世界坐标轴，务必取绝对值（不同朝向分量可能为负）
        double w = getPaintingWidth();
        double h = getPaintingHeight();
        double d = CANVAS_THICKNESS;

        Vec3 sizeVec = up.scale(h).add(cross.scale(w)).add(front.scale(d));
        double sx = Math.abs(sizeVec.x);
        double sy = Math.abs(sizeVec.y);
        double sz = Math.abs(sizeVec.z);

        return AABB.ofSize(center, sx, sy, sz);
    }


//    @Override
//    protected AABB calculateBoundingBox(BlockPos pos, Direction side) {
//        Vec3 front = Vec3.atLowerCornerOf(side.getNormal());
//        Vec3 up = side.getAxis().isVertical() ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
//        Vec3 cross = up.cross(front);
//
//        if (this.rotation != 0) {
//            float rad = (float) (this.rotation * Math.PI / 180.0);
//            up = up.yRot(rad);
//            cross = cross.yRot(rad);
//        }
//
//        double dx = offsetForPaintingSize(getPaintingWidth());
//        double dy = offsetForPaintingSize(getPaintingHeight());
//
//        Direction facing;
//        Direction counter;
//        if (side.getAxis().isVertical()) {
//            facing = Direction.fromYRot(this.rotation);
//            if (side == Direction.UP) {
//                facing = facing.getOpposite();
//                counter = facing.getClockWise();
//            } else {
//                counter = facing.getCounterClockWise();
//            }
//        } else {
//            facing = Direction.UP;
//            counter = side.getCounterClockWise();
//        }
//
//        Vec3 center = Vec3.atCenterOf(pos)
//                .relative(side, -0.46875F)
//                .relative(counter, dx)
//                .relative(facing, dy);
//
//        Vec3 shift = up.scale(getPaintingHeight())
//                .add(cross.scale(getPaintingWidth()))
//                .add(front.scale(0.0625F));
//
//        return AABB.ofSize(center, shift.x(), shift.y(), shift.z());
//    }

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

    // ========= 交互 =========
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (sp.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) return InteractionResult.PASS;
        PacketDistributor.sendToPlayer(sp, new GetFramePayload(this.getId(), this.getFrame()));
        PacketDistributor.sendToPlayer(sp, new GetMaterialPayload(this.getId(), this.getMaterial()));
        PacketDistributor.sendToPlayer(sp, new GetPaintingAutoFitPayload(this.getId(), this.isAutoFit()));
        PacketDistributor.sendToPlayer(sp, new GetPaintingSizePayload(this.getId(), this.getPaintingWidth(), this.getPaintingHeight()));
        PacketDistributor.sendToPlayer(sp, new GetPaintingImagePayload(this.getId(), this.getPaint()));

        PacketDistributor.sendToPlayer(sp, new OpenPaintingScreenPayload(this.getId()));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPushable() {
        return true; // 允许与玩家/实体发生推挤
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        // 关键：屏蔽任何试图推动画作本体的位移
        // HangingEntity 的默认实现会在这里触发掉落，我们不调用 super 即可避免。
        // （如果你想允许极小的“抖动”也没问题，但通常直接忽略最稳妥）
    }

    @Override
    public boolean isPushedByFluid() {
        // 防止水流也来推
        return false;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canBeCollidedWith() { return true; }


    // ========= 物品/音效 =========
    public Item getItem() {
        return ItemRegister.PAINTING_ITEM.get();
    }

    public boolean isGlowing() {
        return false;
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
        builder.define(AUTO_FIT, true);
    }

    // ========= 网络 =========
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity tracker) {
        return new ClientboundAddEntityPacket(this, this.rotation << 4 | (byte)this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket pkt) {
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
        tag.putBoolean("AutoFit", isAutoFit());
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

        this.setAutoFit(tag.contains("AutoFit") ? tag.getBoolean("AutoFit") : true);
        this.recalculateBoundingBox();

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (WIDTH.equals(key) || HEIGHT.equals(key)) {
            this.recalculateBoundingBox();
        }
    }

    // ========= Getter / Setter =========
    public ResourceLocation getPaint() { return this.getEntityData().get(PAINT); }
    public void setPaint(ResourceLocation id) { this.getEntityData().set(PAINT, id); }

    public ResourceLocation getFrame() { return this.getEntityData().get(FRAME); }
    public void setFrame(ResourceLocation id) { this.getEntityData().set(FRAME, id); }

    public ResourceLocation getMaterial() { return this.getEntityData().get(MATERIAL); }
    public void setMaterial(ResourceLocation id) { this.getEntityData().set(MATERIAL, id); }

    public int getPaintingWidth() { return this.getEntityData().get(WIDTH); }
    public void setPaintingWidth(int w) {
        this.getEntityData().set(WIDTH, Math.max(1, w));
        this.recalculateBoundingBox();
    }

    public int getPaintingHeight() { return this.getEntityData().get(HEIGHT); }
    public void setPaintingHeight(int h) {
        this.getEntityData().set(HEIGHT, Math.max(1, h));
        this.recalculateBoundingBox();
    }

    public boolean isAutoFit() {
        return this.getEntityData().get(AUTO_FIT);
    }
    public void setAutoFit(boolean value) {
        this.getEntityData().set(AUTO_FIT, value);
    }



}
