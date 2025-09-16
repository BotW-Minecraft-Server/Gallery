package link.botwmcs.gallery.registration;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.item.PaintingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

public final class ItemRegister {
    public static final ResourceLocation PAINTING_ITEM_ID = Gallery.locate("painting");
    public static final ResourceLocation TAB_ID = Gallery.locate("tab");

    public static Supplier<Item> PAINTING_ITEM;
    public static Supplier<CreativeModeTab> TAB;

    public static void registerItems(RegistryHelper<Item> helper) {
        PAINTING_ITEM = helper.register(PAINTING_ITEM_ID, PaintingItem::new);
    }

    public static void registerCreativeTab(RegistryHelper<CreativeModeTab> helper) {
        TAB = helper.register(TAB_ID, () -> CreativeModeTab.builder()
                .title(Component.literal("Gallery"))
                .icon(() -> new ItemStack(Objects.requireNonNull(PAINTING_ITEM.get())))
                .displayItems((params, output) -> {
                    output.accept(new ItemStack(Objects.requireNonNull(PAINTING_ITEM.get())));
                })
                .build());
    }
    
    private ItemRegister() {}
}

