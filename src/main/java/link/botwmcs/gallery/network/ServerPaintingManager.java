package link.botwmcs.gallery.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.identify.Painting;
import link.botwmcs.gallery.utils.Gsons;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerPaintingManager {
    private final Map<ResourceLocation, Painting> metas = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, PaintingSource> sources = new ConcurrentHashMap<>();

    private final Path storeDir;

    public ServerPaintingManager(MinecraftServer server) {
        this.storeDir = server.getServerDirectory().resolve("images");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException ignore) {}
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Gallery-Index-IO");
        t.setDaemon(true);
        return t;
    });

    // 查询
    public Optional<Painting> getPainting(ResourceLocation motive) {
        return Optional.ofNullable(metas.get(motive));
    }
    public Optional<PaintingSource> getSource(ResourceLocation motive) {
        return Optional.ofNullable(sources.get(motive));
    }

    // 注册一幅画（建议：由命令/GUI调用；server进行域名白名单、内容长度、MIME、哈希校验）
    public ResourceLocation registerPainting(Painting meta, PaintingSource src) {
        ResourceLocation motive = Gallery.locate(meta.authorUUID().toString() + "/" + meta.hash());
        metas.put(motive, meta);
        sources.put(motive, src);
        saveIndexAsync();
        // 广播给在线玩家：S2CPaintingMeta(motive, meta, src)
//        Network.sendToAll(new S2CPaintingMeta(motive, meta, src));
        return motive;
    }

    // 审核/删除/替换等管理接口（可选）
    public void remove(ResourceLocation motive) {
        metas.remove(motive);
        sources.remove(motive);
        saveIndexAsync();
//        Network.sendToAll(new S2CRemovePainting(motive));
    }


    /* ===== 持久化（极简：一个 JSON 存源与元数据） ===== */
    private record PersistRow(String motive, Painting meta, PaintingSource src) {}
    private void loadIndex() {
        Path p = storeDir.resolve("index.json");
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            JsonElement root = JsonParser.parseReader(r);
            for (JsonElement e : root.getAsJsonArray()) {
                PersistRow row = Gsons.GSON.fromJson(e, PersistRow.class);
                ResourceLocation motive = ResourceLocation.parse(row.motive);
                metas.put(motive, row.meta);
                sources.put(motive, row.src);
            }
        } catch (Exception ex) { Gallery.LOGGER.warn("loadIndex failed", ex); }
    }
    private void saveIndexAsync() {
        io.submit(this::saveIndex);
    }
    private void saveIndex() {
        Path p = storeDir.resolve("index.json");
        JsonArray arr = new JsonArray();
        // 快照遍历，避免并发修改异常
        metas.forEach((m, meta) -> {
            PaintingSource src = sources.get(m);
            PersistRow row = new PersistRow(m.toString(), meta, src);
            arr.add(Gsons.GSON.toJsonTree(row));
        });

        try {
            Files.createDirectories(p.getParent());
            // 临时文件 + 原子替换，降低损坏风险
            Path tmp = p.resolveSibling("index.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                Gsons.GSON.toJson(arr, w);
            }
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            Gallery.LOGGER.warn("saveIndex failed", ex);
        }
    }
    // 关闭（服务器停机时调用，防止线程泄漏）
    public void shutdown() {
        io.shutdown();
    }


}
