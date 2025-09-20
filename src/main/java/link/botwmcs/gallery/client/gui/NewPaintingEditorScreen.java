package link.botwmcs.gallery.client.gui;

import link.botwmcs.fizzy.ImageServices;
import link.botwmcs.fizzy.client.elements.FizzyButton;
import link.botwmcs.fizzy.client.elements.StartButton;
import link.botwmcs.fizzy.util.EasyImagesClient;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.network.c2s.SetFramePayload;
import link.botwmcs.gallery.network.c2s.SetMaterialPayload;
import link.botwmcs.gallery.network.c2s.SetPaintingImagePayload;
import link.botwmcs.gallery.util.ClientPaintingImages;
import link.botwmcs.gallery.util.FizzyImageSource;
import link.botwmcs.gallery.util.FrameLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class NewPaintingEditorScreen extends Screen {
    private final int entityId;

    /* -------- 布局常量 -------- */
    private static final int LEFT_W = 128;
    private static final int PADDING = 8;
    private static final int THUMB = 64;
    private static final int GRID_GAP = 8;
    private static final int THUMB_TEX = 1024;

    /* -------- 线程池 / 资源 -------- */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private final Path uploadedDir = Path.of(link.botwmcs.fizzy.Config.IMAGE_LOC.get());
    private static final Pattern HEX40 = Pattern.compile("^[0-9a-f]{40}\\.(png|jpg|jpeg|gif|webp)$", Pattern.CASE_INSENSITIVE);


    /* -------- 页面枚举 -------- */
    private enum Page { UPLOADED, FRAMES }
    private Page page = Page.UPLOADED;

    /* -------- 图片页数据 -------- */
    private List<Path> imgEntries = new ArrayList<>();
    private int selectedImgIndex = -1;

    /* -------- 框架页数据 -------- */
    private List<ResourceLocation> frameList = new ArrayList<>(); // 唯一 frame id（去重）
    private List<ResourceLocation> materials = new ArrayList<>(); // 所有 material id（去重）
    private Map<ResourceLocation, ResourceLocation> materialGui = new HashMap<>();
    private int selectedMaterial = -1;
    // 新增：双向映射
    private final Map<ResourceLocation, List<ResourceLocation>> frameToMaterials = new HashMap<>();
    private final Map<ResourceLocation, ResourceLocation> materialToFrame   = new HashMap<>();
    private static final ResourceLocation FRAME_NONE   = Gallery.locate("objects/frame/none");   // = gallery:objects/frame/none
    private static final ResourceLocation FRAME_SIMPLE = Gallery.locate("objects/frame/simple"); // = gallery:objects/frame/simple
    private static final ResourceLocation MAT_NONE     = Gallery.locate("textures/block/frame/none/none.png");

    /* -------- 通用分页 -------- */
    private int pageIndex = 0;
    private int pageCount = 1;

    /* -------- 控件 -------- */
    private FizzyButton btnUploaded, btnFrames;
    private FizzyButton btnPrev, btnNext;
    private StartButton btnConfirm;

    /* -------- 预览层（仅图片页使用） -------- */
    private boolean previewOpen = false, previewClosing = false;
    private Path previewPath = null;
    private ClientPaintingImages.Thumb previewThumb = null;
    private net.minecraft.client.renderer.Rect2i previewStart = null, previewEnd = null;
    private long previewAnimStartNanos = 0L;
    private static final float PREVIEW_DURATION_SEC = 0.22f;
    private long lastClickAtNs = 0L;
    private int lastClickIndex = -1;
    private static final long DOUBLE_CLICK_NS = 250_000_000L; // 250ms

    public NewPaintingEditorScreen(int entityId) {
        super(Component.empty());
        this.entityId = entityId;
    }

    /* ---------------- init / page 切换 ---------------- */
    @Override
    protected void init() {
        int x = PADDING;
        int y = PADDING + 12;

        // 左侧：上传页
        btnUploaded = FizzyButton.builder(Component.literal("Uploaded Images"), b -> setPage(Page.UPLOADED))
                .pos(x, y).size(LEFT_W - PADDING * 2, 20).build();
        y += 24;

        // 左侧：Frames 页
        btnFrames = FizzyButton.builder(Component.literal("Frames"), b -> setPage(Page.FRAMES))
                .pos(x, y).size(LEFT_W - PADDING * 2, 20).build();
        y += 24;

        addRenderableWidget(btnUploaded);
        addRenderableWidget(btnFrames);

        // 右侧底部翻页/确认
        var right = rightArea();
        int barY = right.y + right.gridH + 8;
        int barW = Math.min(this.width - right.x - PADDING, right.gridW);

        btnPrev = FizzyButton.builder(Component.literal("<"), b -> turnPage(-1))
                .pos(right.x, barY).size(20, 20).build();
        btnNext = FizzyButton.builder(Component.literal(">"), b -> turnPage(+1))
                .pos(right.x + barW - 20, barY).size(20, 20).build();
        btnConfirm = StartButton.builder(Component.literal("Confirm"), b -> onConfirm())
                .pos(right.x + (barW - 100) / 2, barY).size(80, 20).build();

        addRenderableWidget(btnPrev);
        addRenderableWidget(btnNext);
        addRenderableWidget(btnConfirm);

        // 默认进入“已上传”
        setPage(Page.UPLOADED);
        refreshUploaded();
        super.init();
    }

    private void setPage(Page p) {
        // 关预览
        closePreviewImmediately();

        // 按钮状态
        if (btnUploaded != null) btnUploaded.active = (p != Page.UPLOADED);
        if (btnFrames   != null) btnFrames.active   = (p != Page.FRAMES);

        selectedImgIndex = -1; selectedMaterial = -1;
        pageIndex = 0; pageCount = 1;

        if (p == Page.UPLOADED) {
            this.page = Page.UPLOADED;
            refreshUploaded();                  // 这里会异步收集并 warmup 缩略图
        } else {
            refreshFrames();
        }
    }

    private void turnPage(int delta) {
        if (pageIndex + delta < 0 || pageIndex + delta >= pageCount) return;
        pageIndex += delta;
        switch (page) {
            case UPLOADED -> { selectedImgIndex = -1; warmupUploadedPage(); }
            case FRAMES   -> { selectedMaterial = -1; /* 无需预热 */ }
        }
    }

    /* ---------------- 已上传（图片页） ---------------- */
    private void refreshUploaded() {
        pageIndex = 0; selectedImgIndex = -1;
        POOL.submit(() -> {
            try {
                try { Files.createDirectories(uploadedDir); } catch (Exception ignore) {}
                List<Path> list = listImages(uploadedDir);
                if (minecraft != null) minecraft.execute(() -> {
                    imgEntries = list;
                    updatePagination(imgEntries.size());
                    warmupUploadedPage();
                });
            } catch (Exception e) {
                Gallery.LOGGER.warn("[Gallery] refreshUploaded failed", e);
                if (minecraft != null) minecraft.execute(() -> { imgEntries = List.of(); updatePagination(0); });
            }
        });
    }

    private List<Path> listImages(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(p -> {
                        String n = p.getFileName().toString();
                        String lower = n.toLowerCase(Locale.ROOT);
                        boolean supported = lower.endsWith(".png") || lower.endsWith(".jpg") ||
                                lower.endsWith(".jpeg")|| lower.endsWith(".gif") ||
                                lower.endsWith(".webp");
                        // 额外排除：我们的 HTTP 缓存文件
                        if (supported && HEX40.matcher(n).matches()) return false;
                        return supported;
                    })
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
        }

    }

    private void warmupUploadedPage() {
        int start = pageIndex * itemsPerPage();
        int end = Math.min(imgEntries.size(), start + itemsPerPage());
        for (int i = start; i < end; i++) {
            Path p = imgEntries.get(i);
            boolean wantAnim = p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif");
            if (ClientPaintingImages.getCachedThumb(p) == null || wantAnim) {
                ClientPaintingImages.ensureThumb(p, THUMB_TEX, wantAnim)
                        .thenAccept(t -> { if (minecraft != null) minecraft.execute(() -> {}); })
                        .exceptionally(ex -> { Gallery.LOGGER.warn("[Gallery] ensureThumb failed: {}", p, ex); return null; });
            }
        }
    }

    /* ---------------- 框架（Frames页） ---------------- */
    private void refreshFrames() {
        frameToMaterials.clear();
        materialToFrame.clear();
        materialGui.clear();

        // 1) 由 FrameLoader 建立映射
        for (var f : FrameLoader.frames.values()) {
            ResourceLocation fr = f.frame();
            ResourceLocation mat = f.material();
            if (fr == null || mat == null) continue;

            // frame -> materials
            frameToMaterials.computeIfAbsent(fr, k -> new ArrayList<>()).add(mat);
            // material -> frame
            materialToFrame.put(mat, fr);
        }
        // 2) 兜底：如果上游未把 none/simple 完整注入，这里强制补全最基本关系
        frameToMaterials.computeIfAbsent(FRAME_NONE, k -> new ArrayList<>());
        if (!frameToMaterials.get(FRAME_NONE).contains(MAT_NONE)) {
            frameToMaterials.get(FRAME_NONE).add(MAT_NONE);
            materialToFrame.putIfAbsent(MAT_NONE, FRAME_NONE);
        }
        frameToMaterials.computeIfAbsent(FRAME_SIMPLE, k -> new ArrayList<>());

        // 取所有 frame id（去重、排序）
        materials = FrameLoader.frames.values().stream()
                .map(FrameLoader.Frame::material)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(ResourceLocation::compareTo)
                .toList();
        frameList = FrameLoader.frames.values().stream()
                .map(FrameLoader.Frame::frame)
                .distinct()
                .sorted(ResourceLocation::compareTo)
                .toList();

        // 预览材质 GUI 纹理（取该 frame 的第一个 material）
        for (ResourceLocation mat : materials) {
            // 你的 JSON 给的是 block 贴图；GUI 用预览图，把 /block/ 映射到 /gui/
            ResourceLocation gui = ResourceLocation.fromNamespaceAndPath(
                    mat.getNamespace(),
                    mat.getPath().replace("/block/", "/gui/")
            );
            materialGui.put(mat, gui);
        }

        page = Page.FRAMES;
        pageIndex = 0;
        selectedMaterial = -1;
        updatePagination(materials.size());
    }


    /* ---------------- 分页/布局通用 ---------------- */
    private record RightArea(int x, int y, int gridW, int gridH, int cols, int rows) {}
    private RightArea rightArea() {
        int cols = itemsPerRow();
        int rows = rowsPerPage();
        int cellW = THUMB, cellH = THUMB;
        int rightX = LEFT_W + PADDING;
        int rightY = PADDING + 14;
        int gridW = cols * cellW + (cols - 1) * GRID_GAP;
        int gridH = rows * cellH + (rows - 1) * GRID_GAP;
        return new RightArea(rightX, rightY, gridW, gridH, cols, rows);
    }

    private int itemsPerRow() {
        int rightX = LEFT_W + PADDING;
        int rightW = this.width - rightX - PADDING;
        return Math.max(1, (rightW + GRID_GAP) / (THUMB + GRID_GAP));
    }
    private int rowsPerPage() {
        int usableH = this.height - PADDING * 2 - 20 /*翻页栏*/ - 14 /*标题行余量*/;
        return Math.max(1, (usableH + GRID_GAP) / (THUMB + GRID_GAP));
    }
    private int itemsPerPage() { return itemsPerRow() * rowsPerPage(); }

    private void updatePagination(int total) {
        int perPage = itemsPerPage();
        this.pageCount = Math.max(1, (int)Math.ceil(total / (double)perPage));
        this.pageIndex = Math.min(this.pageIndex, pageCount - 1);
    }

    /* ---------------- 渲染 ---------------- */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 左侧背景
        g.fill(PADDING/2, PADDING/2, LEFT_W - PADDING/2, this.height - PADDING/2, 0x66000000);
        drawCentered(g, Component.literal("Gallery"), (LEFT_W)/2, 6, 0xFFFFFF);

        // 右侧网格
        switch (page) {
            case UPLOADED -> renderUploadedGrid(g);
            case FRAMES   -> renderFramesGrid(g);
        }

        // 页脚页码
        var right = rightArea();
        String ps = (pageIndex + 1) + " / " + pageCount + (page == Page.UPLOADED ? ("  (" + imgEntries.size() + ")") : ("  (" + materials.size() + ")"));
        drawCentered(g, Component.literal(ps), right.x + (this.width - right.x - PADDING)/2, this.height - PADDING - 34, 0xFFFFFF);

        // 预览层（仅图片页）
        if (page == Page.UPLOADED) renderPreviewLayer(g);
    }

    private void renderUploadedGrid(GuiGraphics g) {
        var right = rightArea();
        int start = pageIndex * itemsPerPage();
        int cellW = THUMB, cellH = THUMB;

        for (int i = 0; i < right.rows * right.cols; i++) {
            int idx = start + i;
            int cx = i % right.cols;
            int cy = i / right.cols;
            int x = right.x + cx * (cellW + GRID_GAP);
            int y = right.y + cy * (cellH + GRID_GAP);

            g.fill(x - 2, y - 2, x + cellW + 2, y + cellH + 2, 0x44000000);

            if (idx >= imgEntries.size()) continue;
            Path p = imgEntries.get(idx);
            ClientPaintingImages.Thumb t = ClientPaintingImages.getCachedThumb(p);

            if (t == null) {
                g.fill(x, y, x + cellW, y + cellH, 0xFF222222);
                drawCentered(g, Component.literal("..."), x + cellW / 2, y + cellH / 2 - 4, 0xAAAAAA);
                if (i == selectedImgIndex) g.renderOutline(x - 3, y - 3, cellW + 6, cellH + 6, 0xFFFFFFFF);
                continue;
            }

            int texW = t.width(), texH = t.height();
            float s = Math.min(1f, Math.min((float) cellW / texW, (float) cellH / texH));
            int drawW = Math.max(1, (int) (texW * s));
            int drawH = Math.max(1, (int) (texH * s));
            int ox = x + (cellW - drawW) / 2;
            int oy = y + (cellH - drawH) / 2;

            g.blit(t.rl(), ox, oy, drawW, drawH, 0, 0, texW, texH, texW, texH);
            if (i == selectedImgIndex) g.renderOutline(ox - 2, oy - 2, drawW + 4, drawH + 4, 0xFFFFFFFF);
        }
    }

    private void renderFramesGrid(GuiGraphics g) {
        var right = rightArea();
        int start = pageIndex * itemsPerPage();
        int cellW = THUMB, cellH = THUMB;

        for (int i = 0; i < right.rows * right.cols; i++) {
            int idx = start + i;
            int cx = i % right.cols;
            int cy = i / right.cols;
            int x = right.x + cx * (cellW + GRID_GAP);
            int y = right.y + cy * (cellH + GRID_GAP);

            g.fill(x - 2, y - 2, x + cellW + 2, y + cellH + 2, 0x44000000);

            if (idx >= materials.size()) continue;
            ResourceLocation mat = materials.get(idx);
            ResourceLocation guiTex = materialGui.getOrDefault(mat, mat);

            // 材质 GUI 贴图一般是 64x32，这里等比缩放到格子内
            int texW = 64, texH = 32;
            float s = Math.min((float)cellW / texW, (float)cellH / texH);
            int drawW = Math.max(1, (int)(texW * s));
            int drawH = Math.max(1, (int)(texH * s));
            int ox = x + (cellW - drawW) / 2;
            int oy = y + (cellH - drawH) / 2;

            g.blit(guiTex, ox, oy, drawW, drawH, 0, 0, texW, texH, texW, texH);

            if (i == selectedMaterial) g.renderOutline(ox - 2, oy - 2, drawW + 4, drawH + 4, 0xFFFFFFFF);
        }

    }

    /* ---------------- 预览层（与之前一致，仅在图片页） ---------------- */
    private void renderPreviewLayer(GuiGraphics g) {
        if (previewOpen && previewThumb != null && previewStart != null && previewEnd != null) {
            long now = System.nanoTime();
            float t = (now - previewAnimStartNanos) / 1_000_000_000f;
            float raw = clamp01(t / PREVIEW_DURATION_SEC);
            float p = easeInOutCubic(previewClosing ? (1f - raw) : raw);

            int texW = previewThumb.width();
            int texH = previewThumb.height();

            int cx = lerpI(previewStart.getX(), previewEnd.getX(), p);
            int cy = lerpI(previewStart.getY(), previewEnd.getY(), p);
            int cw = lerpI(previewStart.getWidth(), previewEnd.getWidth(), p);
            int ch = lerpI(previewStart.getHeight(), previewEnd.getHeight(), p);

            // 提高 Z：让预览永远在最上层
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);

            // 半透明遮罩（越接近打开越深）
            int alpha = (int) (0xB0 * p) & 0xFF;
            int mask = (alpha << 24);
            g.fill(0, 0, this.width, this.height, mask);

            // —— 6) 关键：blit 的参数顺序
            g.blit(previewThumb.rl(), cx, cy, cw, ch, 0, 0, texW, texH, texW, texH);
            g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFFFFFFF);

            g.pose().popPose();
            if (raw >= 1f) {
                if (previewClosing) {
                    previewOpen = false;
                    previewClosing = false;
                    previewPath = null;
                    previewThumb = null;
                    previewStart = previewEnd = null;
                }
            }
        }
    }

    private void openPreview(Path file, Rect2i startRect) {
        if (page != Page.UPLOADED) return; // 仅图片页
        boolean wantAnim = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif");
        ClientPaintingImages.ensureFull(file, wantAnim)
                .thenAccept(t -> { if (minecraft != null) minecraft.execute(() -> openPreviewInternal(file, t, startRect)); })
                .exceptionally(ex -> { Gallery.LOGGER.warn("[Gallery] ensureFull failed: {}", file, ex); return null; });
    }
    private void openPreviewInternal(Path file, ClientPaintingImages.Thumb t, Rect2i startRect) {
        this.previewPath = file;
        this.previewThumb = t;
        this.previewStart = startRect;
        this.previewEnd   = computeEndRect(t.width(), t.height());
        this.previewOpen = true; this.previewClosing = false;
        this.previewAnimStartNanos = System.nanoTime();
    }
    private void closePreview() { if (previewOpen && !previewClosing) { previewClosing = true; previewAnimStartNanos = System.nanoTime(); } }
    private void closePreviewImmediately() { previewOpen = false; previewClosing = false; previewPath = null; previewThumb = null; previewStart = previewEnd = null; }

    private Rect2i computeEndRect(int texW, int texH) {
        int margin = 24;
        int availW = this.width  - margin*2;
        int availH = this.height - margin*2;
        float s = Math.min((float)availW/texW, (float)availH/texH);
        int dw = Math.max(1, Math.round(texW * s));
        int dh = Math.max(1, Math.round(texH * s));
        int dx = (this.width - dw)/2;
        int dy = (this.height - dh)/2;
        return new Rect2i(dx, dy, dw, dh);
    }

    private Rect2i computeCellRect(int idxInPage) {
        var right = rightArea();
        int cx = idxInPage % right.cols, cy = idxInPage / right.cols;
        int x = right.x + cx * (THUMB + GRID_GAP);
        int y = right.y + cy * (THUMB + GRID_GAP);
        return new Rect2i(x, y, THUMB, THUMB);
    }

    /* ---------------- 输入 ---------------- */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 预览层打开：吃掉点击并关闭
        if (previewOpen) { closePreview(); return true; }

        var right = rightArea();
        int cellW = THUMB, cellH = THUMB;

        // 是否点在网格区域
        boolean inGrid = mouseX >= right.x && mouseY >= right.y &&
                mouseX < this.width - PADDING &&
                mouseY < this.height - PADDING - 28;

        if (inGrid) {
            int gx = (int)(mouseX - right.x);
            int gy = (int)(mouseY - right.y);
            int col = gx / (cellW + GRID_GAP);
            int row = gy / (cellH + GRID_GAP);
            if (col >= 0 && col < right.cols && row >= 0 && row < right.rows) {
                int idxInPage = row * right.cols + col;

                if (page == Page.UPLOADED) {
                    int absoluteIdx = pageIndex * itemsPerPage() + idxInPage;
                    if (absoluteIdx < imgEntries.size()) {
                        long now = System.nanoTime();
                        if (lastClickIndex == absoluteIdx && (now - lastClickAtNs) <= DOUBLE_CLICK_NS) {
                            // 双击：从图片矩形开始放大
                            openPreview(imgEntries.get(absoluteIdx), computeDrawRectInGrid(idxInPage));
                            lastClickIndex = -1;
                            return true;
                        } else {
                            selectedImgIndex = idxInPage;
                            lastClickIndex = absoluteIdx;
                            lastClickAtNs = now;
                            return true;
                        }
                    }
                } else { // FRAMES
                    int absoluteIdx = pageIndex * itemsPerPage() + idxInPage;
                    if (absoluteIdx < materials.size()) {
                        selectedMaterial = idxInPage;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (previewOpen) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (previewOpen) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (previewOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) closePreview();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /* ---------------- 确认 ---------------- */
    private void onConfirm() {
        if (page == Page.UPLOADED) {
            if (selectedImgIndex < 0) {
                toast("请选择一张图片");
                return;
            }
            int abs = pageIndex * itemsPerPage() + selectedImgIndex;
            if (abs >= imgEntries.size()) return;
            Path chosen = imgEntries.get(abs);
            String ext  = FizzyImageSource.extFromFilename(chosen.getFileName().toString()); // png/jpg/...

            // 发送设置图片的 payload
            if (btnConfirm != null) btnConfirm.active = false;
            toast("正在上传图片…");
            POOL.submit(() -> {
                try {
                    byte[] data = Files.readAllBytes(chosen);
                    String sha256 = FizzyImageSource.sha256Hex(data);
                    Path cacheRoot = FizzyImageSource.cacheDir();

                    Path cacheFile = cacheRoot.resolve(sha256 + "." + ext);
                    Path keyFile   = cacheRoot.resolve(sha256 + ".key");

                    Files.createDirectories(cacheRoot);
                    // —— 命中：有图 + 有 key → 不上传、不联网 —— //
                    if (Files.exists(cacheFile) && Files.exists(keyFile)) {
                        String key = Files.readString(keyFile, StandardCharsets.UTF_8).trim(); // yyyy/MM/dd/<sha>-0[_n].ext
                        if (!key.isEmpty()) {
                            ResourceLocation paintId = Gallery.locate("img/" + key);
                            minecraft.execute(() -> {
                                PacketDistributor.sendToServer(new SetPaintingImagePayload(entityId, paintId));
                                if (btnConfirm != null) btnConfirm.active = true;
                                toast("完成！（已复用缓存）");
                                this.onClose();
                            });
                            return;
                        }
                    }

                    // —— 未命中：首次上传（或历史没有 .key） —— //
                    String uploadName = sha256 + "." + ext; // 稳定文件名
                    var r = ImageServices.IMAGES.uploadAsync(data, uploadName, guessMimeType(uploadName)).get();
                    if (r == null || !r.success || r.url == null || r.url.isBlank())
                        throw new IOException("上传失败: http=" + (r == null ? -1 : r.httpCode) + " body=" + (r == null ? "null" : r.rawResponse));

                    String key = extractKeyFromEasyImagesUrl(r.url);           // yyyy/MM/dd/<sha>-0[_n].ext
                    if (key == null || key.isBlank()) throw new IOException("无法解析返回URL: " + r.url);

                    // 预热内容缓存（以最终扩展名存一份；若已有就跳过）
                    String finalExt = FizzyImageSource.extFromFilename(key);
                    Path finalCacheFile = cacheRoot.resolve(sha256 + "." + finalExt);
                    if (!Files.exists(finalCacheFile)) {
                        Files.write(finalCacheFile, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    }

                    // 写 sidecar：以后命中直接用，不再上传/联网
                    Files.writeString(keyFile, key, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    ResourceLocation paintId = Gallery.locate("img/" + key);
                    minecraft.execute(() -> {
                        PacketDistributor.sendToServer(new SetPaintingImagePayload(entityId, paintId));
                        if (btnConfirm != null) btnConfirm.active = true;
                        toast("完成！");
                        this.onClose();
                    });

                } catch (Throwable ex) {
                    Gallery.LOGGER.warn("[Gallery] upload image failed: {}", chosen, ex);
                    if (minecraft != null) minecraft.execute(() -> {
                        if (btnConfirm != null) btnConfirm.active = true;
                        toast("上传失败：" + ex.getMessage());
                    });
                }
            });
            this.onClose();
        } else { // FRAMES
            if (selectedMaterial < 0) {
                toast("请选择一个框架");
                return;
            }
            int abs = pageIndex * itemsPerPage() + selectedMaterial;
            if (abs >= materials.size()) return;

            ResourceLocation mat = materials.get(abs);
            ResourceLocation fr = materialToFrame.get(mat);
            if (fr == null) {
                if (mat.equals(MAT_NONE) || mat.getPath().endsWith("/none/none.png")) {
                    fr = FRAME_NONE;
                } else {
                    fr = FRAME_SIMPLE;
                }
            }
            // 默认选择该 frame 的第一个材质
            if (btnConfirm != null) btnConfirm.active = false;
            ResourceLocation finalFr = fr;
            minecraft.execute(() -> {
                PacketDistributor.sendToServer(new SetFramePayload(entityId, finalFr));
                PacketDistributor.sendToServer(new SetMaterialPayload(entityId, mat));
                if (btnConfirm != null) btnConfirm.active = true;
                toast("完成！");
                this.onClose();
            });
        }
    }

    /* ---------------- Tools ---------------- */

    @Override public boolean isPauseScreen() { return false; }

    private static String guessMimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        } else {
            return !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") ? "application/octet-stream" : "image/jpeg";
        }
    }

    private static @Nullable String extractKeyFromEasyImagesUrl(String url) {
        int i = url.indexOf("/i/");
        if (i < 0) return null;
        String sub = url.substring(i + 3);
        return sub.replaceAll("^/+", ""); // 去掉多余斜杠
    }

    private void drawCentered(GuiGraphics g, Component c, int x, int y, int color) {
        int w = this.font.width(c);
        g.drawString(this.font, c, x - w/2, y, color, false);
    }

    private static float clamp01(float v){ return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static float easeInOutCubic(float t){ return (t < 0.5f) ? 4f*t*t*t : 1f - (float)Math.pow(-2f*t + 2f, 3)/2f; }
    private static int lerpI(int a, int b, float t){ return a + Math.round((b - a)*t); }
    private Rect2i computeDrawRectInGrid(int idxInPage) {
        int rightX = LEFT_W + PADDING;
        int rightY = PADDING + 14;
        int cols = itemsPerRow();
        int cellW = THUMB, cellH = THUMB;

        int cx = idxInPage % cols, cy = idxInPage / cols;
        int x = rightX + cx * (cellW + GRID_GAP);
        int y = rightY + cy * (cellH + GRID_GAP);

        int absoluteIdx = pageIndex * itemsPerPage() + idxInPage;
        if (absoluteIdx >= imgEntries.size()) return new Rect2i(x, y, cellW, cellH);

        Path p = imgEntries.get(absoluteIdx);
        var t = ClientPaintingImages.getCachedThumb(p);
        if (t == null) return new Rect2i(x, y, cellW, cellH);

        int texW = t.width(), texH = t.height();
        float s = Math.min(1f, Math.min((float) cellW / texW, (float) cellH / texH));
        int drawW = Math.max(1, (int)(texW * s));
        int drawH = Math.max(1, (int)(texH * s));
        int ox = x + (cellW - drawW) / 2;
        int oy = y + (cellH - drawH) / 2;

        return new Rect2i(ox, oy, drawW, drawH);
    }


    private void toast(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.getToasts().addToast(SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.literal("Gallery"), Component.literal(msg)));
        }
    }

    // 允许把文件直接拖进窗口：
    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> files) {
        boolean copied = false;
        for (java.nio.file.Path p : files) {
            String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                    || n.endsWith(".gif") || n.endsWith(".webp"))) continue;
            try {
                java.nio.file.Files.createDirectories(uploadedDir);
                java.nio.file.Files.copy(p, uploadedDir.resolve(p.getFileName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copied = true;
            } catch (Exception ex) {
                Gallery.LOGGER.warn("[Gallery] copy dropped file failed: {}", p, ex);
            }
        }
        if (copied) {
            toast("上传成功！");
            refreshUploaded();
        } else {
            toast("没有找到可用的图片文件：只支持 PNG/JPG/GIF/WEBP");
        }
    }

}
