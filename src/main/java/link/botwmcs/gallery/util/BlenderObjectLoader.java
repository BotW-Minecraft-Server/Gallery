package link.botwmcs.gallery.util;

import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.util.oobj.Builder;
import link.botwmcs.gallery.util.oobj.Face;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlenderObjectLoader extends SimplePreparableReloadListener<Map<ResourceLocation, Resource>> {
    protected static final ResourceLocation MODEL_DIR = Gallery.locate("objects");
    public static final Map<ResourceLocation, List<Face>> objects = new HashMap();

    @Override
    protected Map<ResourceLocation, Resource> prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        return resourceManager.listResources("objects", (n) -> n.getPath().endsWith(".obj"));
    }

    @Override
    protected void apply(Map<ResourceLocation, Resource> resourceLocationResourceMap, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        objects.clear();
        resourceLocationResourceMap.forEach((id, res) -> {
            try {
                InputStream stream = res.open();
                ArrayList<Face> faces = (new Builder(new BufferedReader(new InputStreamReader(stream)))).faces;
                ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath());
                objects.put(newId, faces);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
