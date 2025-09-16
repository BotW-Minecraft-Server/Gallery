package link.botwmcs.gallery;

import link.botwmcs.gallery.registration.RendererRegister;
import link.botwmcs.gallery.util.BlenderObjectLoader;
import link.botwmcs.gallery.util.FrameLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.Objects;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Gallery.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Gallery.MODID, value = Dist.CLIENT)
public class GalleryClient {
    public GalleryClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        Gallery.LOGGER.info("HELLO FROM CLIENT SETUP");
        Gallery.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Objects.requireNonNull(event);
        RendererRegister.register(event::registerEntityRenderer);
    }

    @SubscribeEvent
    static void data(FMLConstructModEvent event) {
        ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).registerReloadListener(new BlenderObjectLoader());
        ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).registerReloadListener(new FrameLoader());
    }

//    @SubscribeEvent
//    static void onRegisterReloaders(RegisterClientReloadListenersEvent event) {
//        event.registerReloadListener(new BlenderObjectLoader());
//    }
}
