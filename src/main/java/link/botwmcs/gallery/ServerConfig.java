package link.botwmcs.gallery;

import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLE_BUILTIN_IMGBED = BUILDER
            .comment("Enable built-in image bed for uploaded images")
            .define("enableBuiltInImageBed", true);
    public static final ModConfigSpec.ConfigValue<String> HTTP_SERVER_ADDR = BUILDER
            .comment("Built-in image bed http server address")
            .define("httpServerAddress", "http://localhost");
    public static final ModConfigSpec.IntValue HTTP_SERVER_PORT = BUILDER
            .comment("Built-in image bed http server port")
            .defineInRange("httpServerPort", 8822, 0, 65535);
    public static final ModConfigSpec.LongValue MAX_BYTE = BUILDER
            .comment("Uploaded image max size")
            .defineInRange("maxByte", 20L * 1024 * 1024, 1024L, Long.MAX_VALUE);


//    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
//            .comment("Whether to log the dirt block on common setup")
//            .define("logDirtBlock", true);
//
//    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
//            .comment("A magic number")
//            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);
//
//    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
//            .comment("What you want the introduction message to be for the magic number")
//            .define("magicNumberIntroduction", "The magic number is... ");
//
//    // a list of strings that are treated as resource locations for items
//    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
//            .comment("A list of items to log on common setup.")
//            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

//    private static boolean validateItemName(final Object obj) {
//        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
//    }
}
