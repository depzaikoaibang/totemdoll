package dev.totemdoll.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Settings saved to config/totemdoll.json */
public final class DollConfig {
    public static final String[] MODELS = {"bucket", "flower_bucket", "coffee", "matcha"};
    public static final String[] MODEL_NAMES = {"Bucket", "Flower Bucket", "Coffee", "Matcha Cup"};
    public static final String[] VIEWS = {"first", "third", "gui", "ground", "fixed"};
    public static final String[] VIEW_NAMES = {"First Person", "Third Person", "Inventory (GUI)", "Ground", "Totem Pop / Frame"};

    public String model = "bucket";
    public boolean slim = true;           // true = Alex (slim) -> alex.png, false = Steve (normal) -> steve.png
    public boolean animations = true;
    public boolean layer3d = true;        // 3D skin layer (outer skin layer extruded into voxels)
    public Map<String, View> views = new LinkedHashMap<>();

    public static final class View {
        public float rx, ry, rz;          // degrees
        public float ox, oy, oz;          // offset in pixels (1/16 block)
        public float scale = 1f;
    }

    public View view(String key) {
        return views.computeIfAbsent(key, k -> new View());
    }

    public int modelIndex() {
        for (int i = 0; i < MODELS.length; i++) if (MODELS[i].equals(model)) return i;
        return 0;
    }

    // ------------------------------------------------------------------ io
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DollConfig instance;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("totemdoll.json");
    }

    public static DollConfig get() {
        if (instance == null) {
            DollConfig c = null;
            try {
                Path f = file();
                if (Files.exists(f)) c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), DollConfig.class);
            } catch (Exception ignored) {
            }
            instance = c != null ? c : new DollConfig();
            if (instance.views == null) instance.views = new LinkedHashMap<>();
            if (instance.modelIndex() == 0 && !"bucket".equals(instance.model)) instance.model = "bucket";
        }
        return instance;
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
