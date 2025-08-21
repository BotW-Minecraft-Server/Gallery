package link.botwmcs.gallery.network;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.ServerConfig;
import link.botwmcs.gallery.network.c2s.C2SCreateUploadTicketPayload;
import link.botwmcs.gallery.network.s2c.S2CUploadTicketPayload;
import link.botwmcs.gallery.utils.imgbed.ticket.UploadTicket;
import link.botwmcs.gallery.utils.imgbed.ticket.UploadTicketManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerNetHandlers {
    private static final UploadTicketManager TICKETS = new UploadTicketManager();
    public static void onCreateUploadTicket(final C2SCreateUploadTicketPayload msg, final IPayloadContext ctx) {
        long maxBytes = ServerConfig.MAX_BYTE.getAsLong();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = (ServerPlayer) ctx.player();
            // 1) 权限/限制检查（示例）
            if (!sp.hasPermissions(0)) { // 只允许OP
                Gallery.LOGGER.warn("Player {} tried to request ticket without permission", sp.getGameProfile().getName());
                return;
            }
            if (msg.sizeBytes() > maxBytes) {
                Gallery.LOGGER.warn("Upload too large: {} > {}", msg.sizeBytes(), maxBytes);
                return;
            }
            // 2) 生成一次性票据（TTL 60s）
            UploadTicket t = TICKETS.create(sp, msg.sha256Hex(), msg.ext(), msg.sizeBytes(), 60_000L, msg.meta());

            String base = ServerConfig.HTTP_SERVER_ADDR.get(); // 例如 "http://img.example.com"
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            int port = ServerConfig.HTTP_SERVER_PORT.get();

            String uploadUrl = base + ":" + port + "/upload/" + t.id();

            // 3) 下发票据
            ctx.reply(new S2CUploadTicketPayload(uploadUrl, t.id(), t.expiresAtMs()));
        });
    }

    // 提供给 HTTP 处理器访问（简化起见做个 getter）
    public static UploadTicketManager tickets() { return TICKETS; }
}
