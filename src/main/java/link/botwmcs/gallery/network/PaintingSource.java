package link.botwmcs.gallery.network;

public record PaintingSource (
        String sourceType,
        String locator,
        String sha256LowerHex,
        String format,
        int widthHint,
        int heightHint
){}
