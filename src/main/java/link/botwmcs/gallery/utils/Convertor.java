package link.botwmcs.gallery.utils;

import org.teacon.slides.Slideshow;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.*;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

public final class Convertor {
    private static boolean isWebP(byte[] webp) {
        if (webp.length < 12) {
            return false;
        } else {
            boolean isRiff = webp[0] == 82 && webp[1] == 73 && webp[2] == 70 && webp[3] == 70;
            boolean isWebp = webp[8] == 87 && webp[9] == 69 && webp[10] == 66 && webp[11] == 80;
            return isRiff && isWebp;
        }
    }

    public static byte[] webpToPng(byte[] webp) {
        try {
            if (!isWebP(webp)) {
                return webp;
            } else {
                Iterator<ImageReader> imageReader = ImageIO.getImageReadersByFormatName("webp");
                if (!imageReader.hasNext()) {
                    return webp;
                } else {
                    ImageReader webpReader = (ImageReader)imageReader.next();

                    byte[] var8;
                    try (
                            ByteArrayInputStream bais = new ByteArrayInputStream(webp);
                            MemoryCacheImageInputStream inputStream = new MemoryCacheImageInputStream(bais);
                    ) {
                        webpReader.setInput(inputStream);
                        BufferedImage bufferedImage = webpReader.read(0);

                        try (
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                MemoryCacheImageOutputStream outputStream = new MemoryCacheImageOutputStream(baos);
                        ) {
                            ImageIO.write(bufferedImage, "png", outputStream);
                            outputStream.flush();
                            var8 = baos.toByteArray();
                        }
                    }

                    return var8;
                }
            }
        } catch (Exception e) {
            Slideshow.LOGGER.warn("Failed to convert webp to PNG", e);
            return webp;
        }
    }

    public static void convertStatic(Path in, Path out, String outFormat) throws IOException {
        try (InputStream is = Files.newInputStream(in)) {
            BufferedImage img = ImageIO.read(is); // 依赖插件能直接读 webp/jpg/png…
            if (img == null) throw new IOException("unsupported or corrupt image");
            Files.createDirectories(out.getParent());
            if (!ImageIO.write(img, outFormat, out.toFile())) {
                throw new IOException("no writer for format: " + outFormat);
            }
        }
    }

    public static void convertAnimatedToGif(InputStream in, OutputStream out) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(in);
        Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
        if (!it.hasNext()) throw new IOException("no reader found");
        ImageReader reader = it.next();
        reader.setInput(iis, false);

        int frames = reader.getNumImages(true);
        if (frames <= 1) {
            BufferedImage img = reader.read(0);
            ImageIO.write(img, "gif", out);
            reader.dispose(); iis.close();
            return;
        }

        ImageWriter gifWriter = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam params = gifWriter.getDefaultWriteParam();
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);

        IIOMetadata meta = gifWriter.getDefaultImageMetadata(type, params);
        String metaFormat = meta.getNativeMetadataFormatName();

        // 写序列
        gifWriter.setOutput(ImageIO.createImageOutputStream(out));
        gifWriter.prepareWriteSequence(null);

        for (int i = 0; i < frames; i++) {
            BufferedImage bi = reader.read(i);
            int delayCs = Math.max(1, readDelayCentis(reader, i)); // 以 1/100 秒为单位
            IIOMetadata frameMeta = gifWriter.getDefaultImageMetadata(new ImageTypeSpecifier(bi), params);
            frameMeta = applyGifFrameMeta(frameMeta, metaFormat, delayCs, i == frames - 1);
            IIOImage frame = new IIOImage(bi, null, frameMeta);
            gifWriter.writeToSequence(frame, params);
        }
        gifWriter.endWriteSequence();

        reader.dispose(); iis.close();
    }

    private static int readDelayCentis(ImageReader reader, int frameIndex) throws IOException {
        IIOMetadata md = reader.getImageMetadata(frameIndex);
        for (String name : md.getMetadataFormatNames()) {
            Node root = md.getAsTree(name);
            NodeList list = root.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node n = list.item(i);
                if ("GraphicControlExtension".equals(n.getNodeName())) {
                    NamedNodeMap attrs = n.getAttributes();
                    Node delay = attrs.getNamedItem("delayTime");
                    if (delay != null) return Integer.parseInt(delay.getNodeValue());
                }
            }
        }
        return 10; // 默认 100ms
    }

    private static IIOMetadata applyGifFrameMeta(IIOMetadata meta, String fmt, int delayCs, boolean last) throws IIOInvalidTreeException {
        String root = "javax_imageio_gif_image_1.0";
        if (!fmt.equals(root)) return meta;
        IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(delayCs));
        gce.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode imgDesc = new IIOMetadataNode("ImageDescriptor");
        imgDesc.setAttribute("imageLeftPosition", "0");
        imgDesc.setAttribute("imageTopPosition", "0");
        imgDesc.setAttribute("imageWidth", "0");
        imgDesc.setAttribute("imageHeight", "0");
        imgDesc.setAttribute("interlaceFlag", "FALSE");

        IIOMetadataNode rootNode = new IIOMetadataNode(root);
        rootNode.appendChild(gce);
        rootNode.appendChild(imgDesc);

        meta.mergeTree(root, rootNode);
        return meta;
    }


}
