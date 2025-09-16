package link.botwmcs.gallery;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
//    public static final ModConfigSpec.ConfigValue<String> FIZZY = BUILDER
//            .comment("Built-in image bed server address")
//            .define("serverAddress", "https://mirror.botwmcs.link/eazyimages");
    static final ModConfigSpec SPEC = BUILDER.build();

}
