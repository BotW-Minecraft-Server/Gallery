package link.botwmcs.gallery.network.s2c;

import link.botwmcs.gallery.client.gui.NewPaintingEditorScreen;
import link.botwmcs.gallery.entity.PaintingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPlayHandlers {
    private ClientPlayHandlers() {}
    public static final class PaintingSnapshot {
        public final int entityId;
        // 可为空，逐包填充
        public ResourceLocation paint;
        public ResourceLocation frame;
        public ResourceLocation material;
        public Integer width;
        public Integer height;
        public Boolean autoFit;
//        public ResourceLocation image; // 如果你把“图片文件ID/贴图ID”单独下发

        public PaintingSnapshot(int entityId) { this.entityId = entityId; }
        public boolean isReadyForGui() {
            // 你可以放宽条件；这里示例为：有尺寸即可先开
            return width != null && height != null;
        }
    }

    private static final Map<Integer, PaintingSnapshot> CACHE = new ConcurrentHashMap<>();
    private static PaintingSnapshot snap(int id) {
        return CACHE.computeIfAbsent(id, PaintingSnapshot::new);
    }

    /* ----------------------- 各 Getter 处理 ----------------------- */

    public static void handleGetFrame(final GetFramePayload msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PaintingSnapshot s = snap(msg.entityId());
            s.frame = msg.frameId();
            refreshIfScreenActive(msg.entityId());
        });
    }

    public static void handleGetMaterial(final GetMaterialPayload msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PaintingSnapshot s = snap(msg.entityId());
            s.material = msg.materialId();
            refreshIfScreenActive(msg.entityId());
        });
    }

    public static void handleGetPaintingSize(final GetPaintingSizePayload msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PaintingSnapshot s = snap(msg.entityId());
            s.width = msg.width();
            s.height = msg.height();
            refreshIfScreenActive(msg.entityId());
        });
    }

    public static void handleGetPainting(final GetPaintingImagePayload msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PaintingSnapshot s = snap(msg.entityId());
            s.paint = msg.paintId();
            refreshIfScreenActive(msg.entityId());
        });
    }

    public static void handleGetPaintingAutoFit(final GetPaintingAutoFitPayload msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PaintingSnapshot s = snap(msg.entityId());
            s.autoFit = msg.autoFit();
            refreshIfScreenActive(msg.entityId());
        });
    }

    public static void handleOpenPaintingScreen(final OpenPaintingScreenPayload msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            final int id = msg.entityId();
            PaintingSnapshot s = snap(id);

            // 尽量锁定实体（用于交互位置/朝向等）
            Entity e = mc.level.getEntity(id);
            if (e instanceof PaintingEntity pe) {
                openOrUpdateGui(mc, s, pe);
            } else {
                openOrUpdateGui(mc, s, null);
            }
        });
    }

    /* ----------------------- GUI 刷新/打开 ----------------------- */

    private static void openOrUpdateGui(Minecraft mc, PaintingSnapshot s, PaintingEntity entityOrNull) {
        if (mc.screen instanceof NewPaintingEditorScreen scr &&
                scr.getEntityId() == s.entityId) {
            // 已经开着：增量刷新
            scr.onPaintingSnapshotUpdated(toDto(s));
        } else {
            // 未打开 -> 如果满足最低信息，直接开；否则也可以先开空壳界面再等包
            mc.setScreen(new NewPaintingEditorScreen(s.entityId, toDto(s), entityOrNull));
        }
    }

    private static void refreshIfScreenActive(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NewPaintingEditorScreen scr &&
                scr.getEntityId() == entityId) {
            scr.onPaintingSnapshotUpdated(toDto(snap(entityId)));
        }
    }

    /* ----------------------- DTO 给 GUI ----------------------- */
    public record PaintingDTO(
            int entityId,
            ResourceLocation paint,
            ResourceLocation frame,
            ResourceLocation material,
            Integer width, Integer height,
            Boolean autoFit) {}

    private static PaintingDTO toDto(PaintingSnapshot s) {
        return new PaintingDTO(s.entityId, s.paint, s.frame, s.material, s.width, s.height, s.autoFit);
    }

    /* ----------------------- 给 GalleryClient 的只读入口 ----------------------- */

    /** 允许 GalleryClient 按需读缓存（例如预览缩略图） */
    public static PaintingDTO getCached(int entityId) {
        return toDto(snap(entityId));
    }

    /** 可选：清缓存（实体卸载时调用） */
    public static void evict(int entityId) {
        CACHE.remove(entityId);
    }



}
