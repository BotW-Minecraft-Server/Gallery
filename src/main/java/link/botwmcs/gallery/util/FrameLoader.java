package link.botwmcs.gallery.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.immersive_paintings.Main;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

public class FrameLoader extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation ID = Main.locate("frames");
    public static final Map<ResourceLocation, net.conczin.immersive_paintings.resources.FrameLoader.Frame> frames = new HashMap();
    private static final String DEFAULT_FRAME = Main.locate("frame/simple").toString();
    private static final String DEFAULT_MATERIAL = Main.locate("frame/simple/oak").toString();

    public FrameLoader() {
        super(new Gson(), ID.getPath());
    }

    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        frames.clear();

        for(Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            try {
                JsonObject object = ((JsonElement)entry.getValue()).getAsJsonObject();
                net.conczin.immersive_paintings.resources.FrameLoader.Frame frame = new net.conczin.immersive_paintings.resources.FrameLoader.Frame(ResourceLocation.parse(GsonHelper.getAsString(object, "frame", DEFAULT_FRAME)), GsonHelper.getAsBoolean(object, "diagonals", false), ResourceLocation.parse(GsonHelper.getAsString(object, "material", DEFAULT_MATERIAL)));
                frames.put((ResourceLocation)entry.getKey(), frame);
            } catch (Exception e) {
                Main.LOGGER.error(e);
            }
        }

    }

    public static record Frame(ResourceLocation frame, boolean diagonals, ResourceLocation material) {
    }
}
