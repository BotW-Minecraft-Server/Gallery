package link.botwmcs.gallery.utils.imgbed.ticket;

import link.botwmcs.gallery.identify.Painting;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UploadTicketManager {
    private final Map<String, UploadTicket> map = new ConcurrentHashMap<>();

    public UploadTicket create(ServerPlayer sp, String sha, String ext, long sizeMax, long ttlMillis, Painting meta) {
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        UploadTicket t = new UploadTicket(id, sp.getUUID(), sha.toLowerCase(), ext.toLowerCase(), sizeMax,
                System.currentTimeMillis() + ttlMillis, meta);
        map.put(id, t);
        return t;
    }

    /** 取出并消费（一次性）。无效/过期则 empty。*/
    public Optional<UploadTicket> consume(String id) {
        UploadTicket t = map.remove(id);
        if (t == null || t.expiredNow()) return Optional.empty();
        return Optional.of(t);
    }

}
