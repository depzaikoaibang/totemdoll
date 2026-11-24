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
    public static final String[] MODELS = {"bucket", "flower_bucket", "coffee", "matcha", "foot_wide", "foot_slim"};
    public static final String[] MODEL_NAMES = {"Bucket", "Flower Bucket", "Coffee", "Matcha", "Foot Wide", "Foot Slim"};
    public static final String[] VIEWS = {"first", "third", "gui", "ground", "fixed"};
    public static final String[] VIEW_NAMES = {"First Person", "Third Person", "Inventory (GUI)", "Ground", "Totem Pop / Frame"};

    public boolean enabled = true;        // false = the vanilla Totem of Undying sprite
    public boolean totem3d = true;        // false = flat 2D totem (the pack's "model2d")
    public String model = "bucket";
    public boolean slim = true;           // true = Alex (slim) -> alex.png, false = Steve (normal) -> steve.png
    public boolean animations = true;
    public boolean blink = true;          // eyes blink on the pack's own blink timeline
    public int eyeRowTop = -1;            // face rows (0-7) covered while blinking; -1 = detect from the skin
    public int eyeRowBottom = -1;
    public boolean layer3d = true;        // 3D skin layer (outer skin layer extruded into voxels)
    public Map<String, View> views = new LinkedHashMap<>();

    public static final class View {
        public float rx, ry, rz;          // degrees
        public float ox, oy, oz;          // offset in pixels (1/16 block)
        public float scale = 1f;
    }

    /** First / third person have a separate setting per hand: "first_r", "first_l", "third_r", "third_l". */
    public static boolean hasHands(String view) { return view.equals("first") || view.equals("third"); }

    /** Position settings are stored per model, so Foot and Bucket can be tuned separately. */
    public View view(String key) {
        return views.computeIfAbsent(model + ":" + key, k -> new View());
    }

    public int modelIndex() {
        for (int i = 0; i < MODELS.length; i++) if (MODELS[i].equals(model)) return i;
        return 0;
    }

    public boolean isFoot() { return model.startsWith("foot"); }

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
            boolean known = false;
            for (String m : MODELS) if (m.equals(instance.model)) known = true;
            if (!known) instance.model = "bucket";
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
