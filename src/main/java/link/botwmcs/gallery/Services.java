package link.botwmcs.gallery;

import link.botwmcs.fizzy.ImageServices;
import link.botwmcs.fizzy.util.EasyImagesClient;
import link.botwmcs.gallery.network.ClientPaintingImages;
import link.botwmcs.gallery.util.FizzyImageSource;

public final class Services {
    public static FizzyImageSource IMAGE_SOURCE;

    public static void initImageSource() {
        IMAGE_SOURCE = new FizzyImageSource(ImageServices.IMAGES);
    }
}
