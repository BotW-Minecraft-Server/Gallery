package link.botwmcs.gallery.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Gsons {
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

}
