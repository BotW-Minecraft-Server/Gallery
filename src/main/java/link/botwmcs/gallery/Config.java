package link.botwmcs.gallery;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<String> SERVER_ADDRESS = BUILDER
            .comment("Built-in image bed server address")
            .define("serverAddress", "http://localhost");
    static final ModConfigSpec SPEC = BUILDER.build();

}
