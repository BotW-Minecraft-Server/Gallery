package link.botwmcs.gallery.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GifDecoder {
    public static final class Result {
        public final List<BufferedImage> frames;
        public final int[] delaysMs;
        public Result(List<BufferedImage> frames, int[] delaysMs) {
            this.frames = frames; this.delaysMs = delaysMs;
        }
    }

    public static Result decode(byte[] bytes) throws Exception {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) throw new IllegalStateException("No GIF ImageReader present");
            ImageReader reader = it.next();
            reader.setInput(in, false, false);

            int imageCount = reader.getNumImages(true);
            List<BufferedImage> outFrames = new ArrayList<>(imageCount);
            int[] delays = new int[imageCount];

            BufferedImage canvas = null;
            Graphics2D g = null;

            for (int i = 0; i < imageCount; i++) {
                BufferedImage raw = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                var tree = meta.getAsTree("javax_imageio_gif_image_1.0");

                int delayCs = 10; // 100ms 默认
                int x = 0, y = 0;
                String disposal = "none";

                for (org.w3c.dom.Node n = tree.getFirstChild(); n != null; n = n.getNextSibling()) {
                    String name = n.getNodeName();
                    var attrs = n.getAttributes();
                    if ("GraphicControlExtension".equals(name)) {
                        if (attrs.getNamedItem("delayTime") != null) {
                            delayCs = Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue());
                        }
                        if (attrs.getNamedItem("disposalMethod") != null) {
                            disposal = attrs.getNamedItem("disposalMethod").getNodeValue();
                        }
                    } else if ("ImageDescriptor".equals(name)) {
                        if (attrs.getNamedItem("imageLeftPosition") != null) {
                            x = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                        }
                        if (attrs.getNamedItem("imageTopPosition") != null) {
                            y = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
                        }
                    }
                }

                if (canvas == null) {
                    int cw = Math.max(reader.getWidth(i),  x + raw.getWidth());
                    int ch = Math.max(reader.getHeight(i), y + raw.getHeight());
                    canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
                    g = canvas.createGraphics();
                    g.setComposite(AlphaComposite.SrcOver);
                }

                if ("restoreToBackgroundColor".equals(disposal)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    g.setComposite(AlphaComposite.SrcOver);
                }
                g.drawImage(raw, x, y, null);

                BufferedImage copy = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = copy.createGraphics();
                g2.drawImage(canvas, 0, 0, null);
                g2.dispose();

                outFrames.add(copy);
                delays[i] = Math.max(20, delayCs * 10); // 转毫秒并限制最小值
            }

            if (g != null) g.dispose();
            reader.dispose();
            return new Result(outFrames, delays);
        }
    }

    private GifDecoder() {}

}
