package link.botwmcs.gallery;

import link.botwmcs.gallery.network.ClientNetHandlers;
import link.botwmcs.gallery.network.ServerNetHandlers;
import link.botwmcs.gallery.network.ServerPaintingManager;
import link.botwmcs.gallery.network.c2s.C2SCreateUploadTicketPayload;
import link.botwmcs.gallery.network.s2c.S2CUploadTicketPayload;
import link.botwmcs.gallery.registration.Entities;
import link.botwmcs.gallery.registration.Items;
import link.botwmcs.gallery.registration.GalleryRegisterHelper;
import link.botwmcs.gallery.utils.imgbed.ImageHttpServer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.function.Consumer;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Gallery.MODID)

public class Gallery {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "gallery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ImageHttpServer IMAGE_HTTP;

//    // Create a Deferred Register to hold Blocks which will all be registered under the "gallery" namespace
//    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "gallery" namespace
//    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
//    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "gallery" namespace
//    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
//
//    // Creates a new Block with the id "gallery:example_block", combining the namespace and path
//    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
//    // Creates a new BlockItem with the id "gallery:example_block", combining the namespace and path
//    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
//
    // Creates a new food item with the id "gallery:example_id", nutrition 1 and saturation 2
//    public static final DeferredItem<Item> PAINTING_ITEM = ITEMS.registerSimpleItem("painting_item", new Item.Properties().food(new FoodProperties.Builder()
//            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "gallery:example_tab" for the example item, that is placed after the combat tab
//    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
//            .title(Component.translatable("itemGroup.gallery")) //The language key for the title of your CreativeModeTab
//            .withTabsBefore(CreativeModeTabs.COMBAT)
//            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
//            .displayItems((parameters, output) -> {
//                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
//            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Gallery(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

//        // Register the Deferred Register to the mod event bus so blocks get registered
//        BLOCKS.register(modEventBus);
//        // Register the Deferred Register to the mod event bus so items get registered
//        ITEMS.register(modEventBus);
//        // Register the Deferred Register to the mod event bus so tabs get registered
//        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Gallery) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
//
//        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
//            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
//        }
//
//        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
//
//        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private static <T> void registerHelper(RegisterEvent event, Registry<T> register, Consumer<GalleryRegisterHelper<T>> consumer) {
        event.register(register.key(), registry -> consumer.accept(registry::register));
    }

    @SubscribeEvent
    private void register(RegisterEvent event) {
        registerHelper(event, BuiltInRegistries.ITEM, Items::registerItems);
        registerHelper(event, BuiltInRegistries.CREATIVE_MODE_TAB, Items::registerCreativeTabs);

        registerHelper(event, BuiltInRegistries.ENTITY_TYPE, Entities::registerEntities);
        registerHelper(event, NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, Entities::registerEntitySerializers);
    }

    @SubscribeEvent
    private void registerNetworks(final RegisterPayloadHandlersEvent event) {
        var r = event.registrar(Gallery.MODID);
        r.playToServer(C2SCreateUploadTicketPayload.TYPE, C2SCreateUploadTicketPayload.STREAM_CODEC, ServerNetHandlers::onCreateUploadTicket);
        r.playToClient(S2CUploadTicketPayload.TYPE, S2CUploadTicketPayload.STREAM_CODEC, ClientNetHandlers::onS2CUploadTicket);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        // Do something when the server starts
        LOGGER.info("[Gallery] Loading server...");
        if (ServerConfig.ENABLE_BUILTIN_IMGBED.get()) {
            LOGGER.info("[Gallery] Starting built-in image bed http server...");
            try {
                ServerPaintingManager manager = new ServerPaintingManager(e.getServer());
                IMAGE_HTTP = new ImageHttpServer(e.getServer(), manager,
                        ServerConfig.HTTP_SERVER_ADDR.get().startsWith("http") ? "0.0.0.0" : ServerConfig.HTTP_SERVER_ADDR.get(),
                        ServerConfig.HTTP_SERVER_PORT.get());
                IMAGE_HTTP.start();
                Gallery.LOGGER.info("Image host started at {}:{}", ServerConfig.HTTP_SERVER_ADDR.get(), ServerConfig.HTTP_SERVER_PORT.get());
            } catch (Exception ex) {
                Gallery.LOGGER.error("Failed to start image host", ex);
            }

        }

    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent e) {
        if (IMAGE_HTTP != null) {
            LOGGER.info("[Gallery] Now stopping built-in image bed http server...");
            IMAGE_HTTP.stop();
            IMAGE_HTTP = null;
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {


    }

    public static ResourceLocation locate(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
