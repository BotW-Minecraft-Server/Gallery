package link.botwmcs.gallery.client.gui;

import link.botwmcs.fizzy.client.elements.FizzyButton;
import link.botwmcs.fizzy.client.elements.StartButton;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.util.ClientPaintingImages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class PaintingEditorScreen extends Screen {
    private final int entityId;

    // 左侧栏 & 布局
    private static final int LEFT_W = 128;
    private static final int PADDING = 8;
    private static final int THUMB = 64;
    private static final int GRID_GAP = 8;

    // 顶部常量与字段
    private static final int THUMB_CELL = 64;
    private static final int THUMB_TEX  = 1024; // 生成更大的缩略图避免糊
    private boolean uploadMode = false;        // 拖拽上传模式

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    // 资源
    private final Path uploadedDir = Path.of(link.botwmcs.fizzy.Config.IMAGE_LOC.get());
    // 数据
    private List<Path> entries = new ArrayList<>();
    private final Map<Path, ResourceLocation> thumbs = new ConcurrentHashMap<>();
    private int page = 0;
    private int pageCount = 1;
    private int selectedIndex = -1; // 当前页内索引
    // 控件
    private FizzyButton btnUploaded, btnUploadNew;
    private FizzyButton btnPrev, btnNext;
    private StartButton btnConfirm;



    public PaintingEditorScreen(int entityId) {
        super(Component.empty());
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        // LEFT PADDING
        int x = PADDING;
        int y = PADDING + 12;

        btnUploaded = FizzyButton.builder(Component.literal("Uploaded Images"), b -> {
            uploadMode = false;
            refreshUploaded();
        }).pos(x, y).size(LEFT_W - PADDING * 2, 20).build();
        y += 24;

        btnUploadNew = FizzyButton.builder(Component.literal("Upload new Image"), b -> {
            uploadMode = true;
        }).pos(x, y).size(LEFT_W - PADDING * 2, 20).build();

        addRenderableWidget(btnUploaded);
        addRenderableWidget(btnUploadNew);

        // RIGHT PADDING
        int rightX = LEFT_W + PADDING;
        int rightY = PADDING + 14;
        int cols   = itemsPerRow();
        int rows   = rowsPerPage();
        int cellW  = THUMB, cellH = THUMB;
        // 网格实际高度
        int gridW = cols * cellW + (cols - 1) * GRID_GAP;
        int gridH = rows * cellH + (rows - 1) * GRID_GAP;

        int barY  = rightY + gridH + 8;                // 底部工具栏 Y
        int barW  = Math.min(this.width - rightX - PADDING, gridW);

        btnPrev = FizzyButton.builder(Component.literal("<"), b -> {
            if (page > 0) {
                page--;
                selectedIndex = -1;
                warmupPage();
            }
        }).pos(rightX, barY).size(20, 20).build();

        btnNext = FizzyButton.builder(Component.literal(">"), b -> {
            if (page + 1 < pageCount) {
                page++;
                selectedIndex = -1;
                warmupPage();
            }
        }).pos(rightX + barW - 20, barY).size(20, 20).build();

        btnConfirm = StartButton.builder(Component.literal("Confirm"), b -> onConfirm())
                .pos(rightX + (barW - 100) / 2, barY).size(80, 20).build();

        addRenderableWidget(btnPrev);
        addRenderableWidget(btnNext);
        addRenderableWidget(btnConfirm);

        // Init uploaded
        refreshUploaded();

        super.init();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // The game will not pause when this screen is open
    }

    private void refreshUploaded() {
        page = 0; selectedIndex = -1;
        POOL.submit(() -> {
            try {
                // 确保目录存在
                try { Files.createDirectories(uploadedDir); } catch (Exception ignore) {}

                List<Path> list = listImages(uploadedDir);
                if (minecraft != null) {
                    minecraft.execute(() -> {
                        entries = list;
                        updatePagination();
                        warmupPage();
                    });
                }
            } catch (Exception e) {
                Gallery.LOGGER.warn("[Gallery] refreshUploaded failed", e);
                if (minecraft != null) {
                    minecraft.execute(() -> { entries = List.of(); updatePagination(); });
                }
            }
        });
    }

    private List<Path> listImages(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                                || n.endsWith(".gif") || n.endsWith(".webp");
                    })
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .collect(Collectors.toList());
        }
    }

    private void updatePagination() {
        int perPage = itemsPerPage();
        this.pageCount = Math.max(1, (int)Math.ceil(entries.size() / (double)perPage));
        this.page = Math.min(this.page, pageCount - 1);
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

    private int itemsPerPage() {
        return itemsPerRow() * rowsPerPage();
    }

    private static boolean isGif(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".gif");
    }

    private void warmupPage() {
        int start = page * itemsPerPage();
        int end = Math.min(entries.size(), start + itemsPerPage());
        for (int i = start; i < end; i++) {
            Path p = entries.get(i);
            boolean wantAnim = isGif(p);
            if (ClientPaintingImages.getCachedThumb(p) == null || wantAnim) {
                ClientPaintingImages.ensureThumb(p, THUMB_TEX, wantAnim)
                        .thenAccept(t -> { if (minecraft != null) minecraft.execute(() -> {}); })
                        .exceptionally(ex -> { Gallery.LOGGER.warn("[Gallery] ensureThumb failed: {}", p, ex); return null; });
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 左栏
        g.fill(PADDING/2, PADDING/2, LEFT_W - PADDING/2, this.height - PADDING/2, 0x66000000);
        drawCentered(g, Component.literal("Gallery"), (LEFT_W)/2, 6, 0xFFFFFF);

        // 右侧网格
        int start = page * itemsPerPage();
        int cols = itemsPerRow();
        int rows = rowsPerPage();

        int rightX = LEFT_W + PADDING;
        int rightY = PADDING + 14;
        int cellW = THUMB, cellH = THUMB;

        if (uploadMode) {
            int gridW  = cols * THUMB_CELL + (cols - 1) * GRID_GAP;
            int gridH  = rows * THUMB_CELL + (rows - 1) * GRID_GAP;
            int x0 = rightX, y0 = rightY, x1 = rightX + gridW, y1 = rightY + gridH;

            // 半透明底 + 边框
            g.fill(x0, y0, x1, y1, 0x44000000);
            g.renderOutline(x0, y0, gridW, gridH, 0xFFFFFFFF);

            // 大字提示
            var title = Component.literal("Drag Image Here To Upload");
            int tw = this.font.width(title);
            g.drawString(this.font, title, x0 + (gridW - tw) / 2, y0 + gridH / 2 - 4, 0xFFFFFF, false);

            // 底部按钮仍按原位置绘制（Prev/Confirm/Next）
            return; // 不渲染网格
        }


        for (int i = 0; i < rows * cols; i++) {
            int idx = start + i;
            int cx = i % cols;
            int cy = i / cols;
            int x = rightX + cx * (cellW + GRID_GAP);
            int y = rightY + cy * (cellH + GRID_GAP);

            g.fill(x - 2, y - 2, x + cellW + 2, y + cellH + 2, 0x44000000);

            if (idx >= entries.size()) continue;

            Path p = entries.get(idx);
            ClientPaintingImages.Thumb t = ClientPaintingImages.getCachedThumb(p);

            if (t == null) {
                g.fill(x, y, x + cellW, y + cellH, 0xFF222222);
                drawCentered(g, Component.literal("..."), x + cellW / 2, y + cellH / 2 - 4, 0xAAAAAA);
                if (i == selectedIndex) g.renderOutline(x - 3, y - 3, cellW + 6, cellH + 6, 0xFFFFFFFF);
                continue;
            }

            int texW = t.width(), texH = t.height();
            // 不放大，只缩小：等比缩放到格子内
            float s = Math.min(1f, Math.min((float) cellW / texW, (float) cellH / texH));
            int drawW = Math.max(1, (int) (texW * s));
            int drawH = Math.max(1, (int) (texH * s));
            int ox = x + (cellW - drawW) / 2;
            int oy = y + (cellH - drawH) / 2;

            // 关键：用 drawW/drawH 作为“目标绘制尺寸”，texW/texH 作为“源纹理尺寸”
            // g.blit(t.rl(), ox, oy, 0, 0, drawW, drawH, texW, texH);
            g.blit(t.rl(), ox, oy, drawW, drawH, 0, 0, texW, texH, texW, texH);

            if (i == selectedIndex) {
                g.renderOutline(ox - 2, oy - 2, drawW + 4, drawH + 4, 0xFFFFFFFF);
            }
        }

        String ps = (page + 1) + " / " + pageCount + "  (" + entries.size() + ")";
        drawCentered(g, Component.literal(ps),
                rightX + (this.width - rightX - PADDING)/2, this.height - PADDING - 34, 0xFFFFFF);



    }

    private void drawCentered(GuiGraphics g, Component c, int x, int y, int color) {
        int w = this.font.width(c);
        g.drawString(this.font, c, x - w/2, y, color, false);
    }

    /* ---------------- 输入 ---------------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rightX = LEFT_W + PADDING;
        int rightY = PADDING + 14;
        int cols = itemsPerRow();
        int rows = rowsPerPage();
        int cellW = THUMB, cellH = THUMB;

        if (mouseX >= rightX && mouseY >= rightY &&
                mouseX < this.width - PADDING &&
                mouseY < this.height - PADDING - 28) {

            int gx = (int)(mouseX - rightX);
            int gy = (int)(mouseY - rightY);

            int col = gx / (cellW + GRID_GAP);
            int row = gy / (cellH + GRID_GAP);

            if (col >= 0 && col < cols && row >= 0 && row < rows) {
                int idxInPage = row * cols + col;
                int absoluteIdx = page * itemsPerPage() + idxInPage;
                if (absoluteIdx < entries.size()) {
                    selectedIndex = idxInPage;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /* ---------------- 动作 ---------------- */

    private void onConfirm() {
        if (selectedIndex < 0) return;
        int absolute = page * itemsPerPage() + selectedIndex;
        if (absolute >= entries.size()) return;

        Path chosen = entries.get(absolute);

        // TODO:
        // 1) 上传 chosen 获取 URL（或直接映射为本地→URL）；
        // 2) 编码为 PAINT id（如 gallery:url/<base64url>）；
        // 3) 发 SetPaintingPayload(entityId, paintId) 给服务端，服务端 setPaint 同步。
        this.onClose();
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
            uploadMode = false;
            refreshUploaded();
        } else {
            toast("没有找到可用的图片文件：只支持 PNG/JPG/GIF/WEBP");
        }
    }

    private void openFileChooser() {
        // 1) headless 场景直接提示
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            toast("当前环境禁用 AWT：请将图片文件拖拽到窗口，或手动复制到 .minecraft/gallery_cache/");
            return;
        }

        try {
            // 2) 在 AWT 事件线程里打开原生文件对话框
            java.awt.EventQueue.invokeAndWait(() -> {
                try {
                    java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Choose an image", java.awt.FileDialog.LOAD);
                    fd.setMultipleMode(false);
                    // 选个默认目录，避免有的平台为空导致不显示
                    String home = System.getProperty("user.home");
                    if (home != null) fd.setDirectory(home);
                    fd.setVisible(true); // 阻塞直到关闭

                    String dir = fd.getDirectory();
                    String file = fd.getFile();
                    if (dir != null && file != null) {
                        java.nio.file.Path chosen = java.nio.file.Paths.get(dir, file);
                        copyToCache(chosen);
                        // 回主线程刷新
                        if (this.minecraft != null) this.minecraft.execute(this::refreshUploaded);
                    }
                } catch (Throwable awtEx) {
                    // 3) 兜底：Swing 文件选择器
                    try {
                        javax.swing.JFileChooser ch = new javax.swing.JFileChooser();
                        ch.setDialogTitle("Choose an image");
                        ch.setMultiSelectionEnabled(false);
                        int res = ch.showOpenDialog(null);
                        if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                            java.nio.file.Path chosen = ch.getSelectedFile().toPath();
                            copyToCache(chosen);
                            if (this.minecraft != null) this.minecraft.execute(this::refreshUploaded);
                        }
                    } catch (Throwable swingEx) {
                        link.botwmcs.gallery.Gallery.LOGGER.warn("[Gallery] file chooser failed", swingEx);
                        toast("打开文件选择器失败：请把图片拖拽到窗口，或复制到 gallery_cache 后点『Uploaded Images』。");
                    }
                }
            });
        } catch (Throwable e) {
            link.botwmcs.gallery.Gallery.LOGGER.warn("[Gallery] openFileChooser crashed", e);
            toast("打开文件选择器异常：请拖拽图片到窗口，或复制到 gallery_cache。");
        }
    }

    private void copyToCache(java.nio.file.Path chosen) throws java.io.IOException {
        java.nio.file.Files.createDirectories(uploadedDir);
        java.nio.file.Files.copy(chosen, uploadedDir.resolve(chosen.getFileName()),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void toast(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.getToasts().addToast(SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.literal("Gallery"), Component.literal(msg)));
        }
    }

}
