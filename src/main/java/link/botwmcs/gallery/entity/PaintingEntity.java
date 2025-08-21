package link.botwmcs.gallery.entity;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.registration.Entities;
import link.botwmcs.gallery.registration.Items;
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
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

public class PaintingEntity extends HangingEntity {
    protected static final Predicate<Entity> PREDICATE = (entity -> entity instanceof PaintingEntity);
    private static final EntityDataAccessor<ResourceLocation> MOTIVE;
    private static final EntityDataAccessor<ResourceLocation> FRAME;
    private static final EntityDataAccessor<ResourceLocation> MATERIAL;
    private static final EntityDataAccessor<Integer> WIDTH;
    private static final EntityDataAccessor<Integer> HEIGHT;

    // private static final EntityDataAccessor<String> IMG_LOCATION;

    private int rotation;

    public PaintingEntity(EntityType<? extends HangingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void setDirection(Direction direction) {
        this.setDirection(direction, this.rotation);
    }

    public void setDirection(Direction direction, int rotation) {
        if (direction != null) {
            this.direction = direction;
            this.rotation = rotation;
            if (direction.getAxis().isHorizontal()) {
                this.absRotateTo((float)(direction.get2DDataValue() * 90), 0.0F);
            } else {
                this.absRotateTo((float)rotation, direction == Direction.UP ? 90.0F : -90.0F);
            }

            this.recalculateBoundingBox();
        }
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos blockPos, Direction side) {
        Vec3 front = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 up = side.getAxis().isVertical() ? new Vec3((double)0.0F, (double)0.0F, (double)1.0F) : new Vec3((double)0.0F, (double)1.0F, (double)0.0F);
        Vec3 cross = up.cross(front);
        if (this.rotation != 0) {
            float radians = (float)((double)this.rotation * Math.PI / (double)180.0F);
            up = up.yRot(radians);
            cross = cross.yRot(radians);
        }

        double dx = this.offsetForPaintingSize(this.getPaintingWidth());
        double dy = this.offsetForPaintingSize(this.getPaintingHeight());
        Direction facing;
        Direction counter;
        if (side.getAxis().isVertical()) {
            facing = Direction.fromYRot((double)this.rotation);
            if (side.equals(Direction.UP)) {
                facing = facing.getOpposite();
                counter = facing.getClockWise();
            } else {
                counter = facing.getCounterClockWise();
            }
        } else {
            facing = Direction.UP;
            counter = side.getCounterClockWise();
        }

        Vec3 vec3d = Vec3.atCenterOf(pos).relative(side, (double)-0.46875F).relative(counter, dx).relative(facing, dy);
        Vec3 shift = up.scale((double)this.getPaintingHeight()).add(cross.scale((double)this.getPaintingWidth())).add(front.scale((double)0.0625F));
        return AABB.ofSize(vec3d, shift.x(), shift.y(), shift.z());
    }

    private double offsetForPaintingSize(int size) {
        return size % 2 == 0 ? (double)0.5F : (double)0.0F;
    }

    @Override
    public boolean survives() {
        BlockPos blockPos = this.pos.relative(this.direction.getOpposite());
        BlockState blockState = this.level().getBlockState(blockPos);
        return !blockState.isSolid() && !DiodeBlock.isDiode(blockState) ? false : this.level().getEntities(this, this.getBoundingBox(), PREDICATE).stream().noneMatch((v) -> ((PaintingEntity)v).direction == this.direction);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
//                if (!XercaPaintCompat.interactWithPainting(this, player, hand)) {
//                    CommonConfig config = Configs.COMMON;
//                    NetworkHandler.sendToClient(serverPlayer, new OpenGuiPayload(OpenGuiPayload.GuiType.EDITOR, this.getId(), config.minPaintingResolution, config.maxPaintingResolution, config.showOtherPlayersPaintings, config.uploadPermissionLevel));
//                }
                // todo: opengui
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;

    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (entity instanceof Player playerEntity && playerEntity.hasInfiniteMaterials()) {
                return;
            }

            spawnAtLocation(this.getItem());
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(this.getItem());
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        this.setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }


    @Override
    public void playPlacementSound() {
        playSound(SoundEvents.PAINTING_PLACE, 1.0f, 1.0f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // todo: need to replace a placeholder image
        // like: http://images.botwmcs.link/images/placeholder.png
        builder.define(MOTIVE, Gallery.locate("none"));
        builder.define(FRAME, Gallery.locate("none"));
        builder.define(MATERIAL, Gallery.locate("none"));
        builder.define(WIDTH, 1);
        builder.define(HEIGHT, 1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        // todo
//        if (key == MOTIVE) {
//            Optional<Painting> painting = level().isClientSide
//                    ? ClientPaintingManager.getPainting(getMotive())
//                    : ServerPaintingManager.getPainting(getServer(), getMotive());
//
//            painting.ifPresent(p -> {
//                        getEntityData().set(WIDTH, Math.max(p.width(), 1));
//                        getEntityData().set(HEIGHT, Math.max(p.height(), 1));
//                        recalculateBoundingBox();
//                    });
//        }


//        if (MOTIVE.equals(data)) {
//            Optional<Painting> painting;
//            if (level().isClientSide) {
//                painting = ClientPaintingManager.getPainting(getMotive());
//            } else {
//                painting = ServerPaintingManager.getPainting(getServer(), getMotive());
//            }
//
//            painting.ifPresent((p) -> {
//                getEntityData().set(WIDTH, Math.max(p.width(), 1));
//                getEntityData().set(HEIGHT, Math.max(p.height(), 1));
//
//                recalculateBoundingBox();
//            });
//        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        return new ClientboundAddEntityPacket(this, (rotation << 4) | (byte)direction.get3DDataValue(), getPos());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        SynchedEntityData tracker = getEntityData();
        nbt.putString("Motive", tracker.get(MOTIVE).toString());
        nbt.putString("Frame", tracker.get(FRAME).toString());
        nbt.putString("Material", tracker.get(MATERIAL).toString());
        nbt.putInt("Facing", direction.get3DDataValue());
        nbt.putInt("Rotation", rotation);

        super.addAdditionalSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        setFrame(ResourceLocation.parse(nbt.getString("Frame")));
        setMaterial(ResourceLocation.parse(nbt.getString("Material")));
        this.direction = Direction.from3DDataValue(nbt.getInt("Facing"));
        this.rotation = nbt.getInt("Rotation");

        // Wait until additional data has been loaded to set motive,
        // saving the number of times that the painting calculates its bounding box.
        super.readAdditionalSaveData(nbt);
        setMotive(ResourceLocation.parse(nbt.getString("Motive")));

    }

    public Item getItem() {
        return Items.PAINTING;
    }

    public boolean isGraffiti() {
        return false;
    }

    public boolean isGlowing() {
        return false;
    }

    public int getPaintingWidth() {
        return getEntityData().get(WIDTH);
    }

    public int getPaintingHeight() {
        return getEntityData().get(HEIGHT);
    }

    public ResourceLocation getMotive() {
        return getEntityData().get(MOTIVE);
    }

    public ResourceLocation getMaterial() {
        return getEntityData().get(MATERIAL);
    }

    public void setMotive(ResourceLocation motive) {
        getEntityData().set(MOTIVE, motive);
    }

    public ResourceLocation getFrame() {
        return getEntityData().get(FRAME);
    }

    public void setFrame(ResourceLocation frame) {
        getEntityData().set(FRAME, frame);
    }

    public void setMaterial(ResourceLocation material) {
        getEntityData().set(MATERIAL, material);
    }

    static {
        MOTIVE = SynchedEntityData.defineId(PaintingEntity.class, Entities.TRACKED_IDENTIFIER);
        FRAME = SynchedEntityData.defineId(PaintingEntity.class, Entities.TRACKED_IDENTIFIER);
        MATERIAL = SynchedEntityData.defineId(PaintingEntity.class, Entities.TRACKED_IDENTIFIER);
        WIDTH = SynchedEntityData.defineId(PaintingEntity.class, EntityDataSerializers.INT);
        HEIGHT = SynchedEntityData.defineId(PaintingEntity.class, EntityDataSerializers.INT);
    }

}
