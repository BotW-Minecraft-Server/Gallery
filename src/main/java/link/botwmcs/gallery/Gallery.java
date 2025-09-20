package link.botwmcs.gallery;


import link.botwmcs.gallery.entity.PaintingEntity;
import link.botwmcs.gallery.network.c2s.SetFramePayload;
import link.botwmcs.gallery.network.c2s.SetMaterialPayload;
import link.botwmcs.gallery.network.c2s.SetPaintingImagePayload;
import link.botwmcs.gallery.registration.EntityRegister;
import link.botwmcs.gallery.registration.ItemRegister;
import link.botwmcs.gallery.registration.RegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.server.commands.ServerPackCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.function.Supplier;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Gallery.MODID)

public class Gallery {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "gallery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gallery(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        RegistryHelper<EntityDataSerializer<?>> serializers = registryOf(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, modEventBus);
        EntityRegister.registerEntitySerializers(serializers);

        RegistryHelper<EntityType<?>> entities = registryOf(BuiltInRegistries.ENTITY_TYPE, modEventBus);
        EntityRegister.registerEntities(entities);

        RegistryHelper<Item> items = registryOf(BuiltInRegistries.ITEM, modEventBus);
        ItemRegister.registerItems(items);

        RegistryHelper<CreativeModeTab> tabs = registryOf(BuiltInRegistries.CREATIVE_MODE_TAB, modEventBus);
        ItemRegister.registerCreativeTab(tabs);

    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        // Services.initImageSource();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(MODID);
        r.playToServer(SetPaintingImagePayload.TYPE, SetPaintingImagePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                ServerPlayer serverPlayer = (ServerPlayer) ctx.player();
                if (serverPlayer == null) return;

                var level = serverPlayer.level();
                var entity = level.getEntity(payload.entityId());
                if (!(entity instanceof PaintingEntity pe)) return;
                pe.setPaint(payload.paintId());

            });
        });

        r.playToServer(SetFramePayload.TYPE, SetFramePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                ServerPlayer serverPlayer = (ServerPlayer) ctx.player();
                if (serverPlayer == null) return;

                var level = serverPlayer.level();
                var entity = level.getEntity(payload.entityId());
                if (!(entity instanceof PaintingEntity pe)) return;
                pe.setFrame(payload.frameId());
            });
        });

        r.playToServer(SetMaterialPayload.TYPE, SetMaterialPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                ServerPlayer serverPlayer = (ServerPlayer) ctx.player();
                if (serverPlayer == null) return;

                var level = serverPlayer.level();
                var entity = level.getEntity(payload.entityId());
                if (!(entity instanceof PaintingEntity pe)) return;
                pe.setMaterial(payload.materialId());
            });
        });
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        // Do something when the server starts
        LOGGER.info("[Gallery] Loading server...");

    }


    public static ResourceLocation locate(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /* ================= 内联的 RegistryHelpers 工厂 ================= */

    /** 传入原版注册表对象（如 BuiltInRegistries.ITEM / ENTITY_TYPE / DATA_SERIALIZER / CREATIVE_MODE_TAB） */
    public static <T> RegistryHelper<T> registryOf(Registry<T> vanillaRegistry, IEventBus modBus) {
        DeferredRegister<T> dr = DeferredRegister.create(vanillaRegistry, MODID);
        dr.register(modBus);
        return makeHelper(dr);
    }

    /** 传入注册表键（原版或 NeoForge 的 ResourceKey 都可） */
    public static <T> RegistryHelper<T> registryOf(ResourceKey<? extends Registry<T>> key, IEventBus modBus) {
        DeferredRegister<T> dr = DeferredRegister.create(key, MODID);
        dr.register(modBus);
        return makeHelper(dr);
    }

    /** 传入注册表名字（ResourceLocation），用于自定义/NeoForge 注册表 */
    public static <T> RegistryHelper<T> registryOf(ResourceLocation registryName, IEventBus modBus) {
        DeferredRegister<T> dr = DeferredRegister.create(registryName, MODID);
        dr.register(modBus);
        return makeHelper(dr);
    }

    private static <T> RegistryHelper<T> makeHelper(DeferredRegister<T> dr) {
        // 注意：DeferredRegister 使用 MODID 作为命名空间，所以只需传 path
        return new RegistryHelper<>() {
            @Override
            public <I extends T> Supplier<I> register(ResourceLocation id, Supplier<? extends I> sup) {
                return dr.register(id.getPath(), sup);
            }
        };
    }
}
