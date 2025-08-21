package link.botwmcs.gallery.registration;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.item.PaintingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class Items {
    public static final PaintingItem PAINTING = new PaintingItem();
    public static final CreativeModeTab PAINTING_TAB = CreativeModeTab.builder((CreativeModeTab.Row)null, -1).title(Component.translatable("itemGroup.gallery")).icon(() -> new ItemStack(PAINTING)).displayItems((params, output) -> {
        output.accept(PAINTING);
//        output.accept(GLOW_PAINTING);
//        output.accept(GRAFFITI);
//        output.accept(GLOW_GRAFFITI);
    }).build();

    public static void registerItems(GalleryRegisterHelper<Item> helper) {
        helper.register(Gallery.locate("painting"), PAINTING);
//        helper.register(Main.locate("glow_painting"), GLOW_PAINTING);
//        helper.register(Main.locate("graffiti"), GRAFFITI);
//        helper.register(Main.locate("glow_graffiti"), GLOW_GRAFFITI);
    }

    public static void registerCreativeTabs(GalleryRegisterHelper<CreativeModeTab> helper) {
        helper.register(Gallery.locate("paintings"), PAINTING_TAB);
    }
}
