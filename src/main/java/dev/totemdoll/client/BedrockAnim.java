package dev.totemdoll.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bedrock .animation.json player (keyframes: linear + catmullrom, Molang expressions). */
public final class BedrockAnim {
    private static final String PREFIX = "animation.oreville_ans.";

    /** One channel (rotation / position / scale) of one bone. */
    private static final class Channel {
        Object[] constant;                 // 3 values (Double or String) when not keyframed
        List<Key> keys;                    // sorted keyframes when keyframed
    }

    private static final class Key {
        float time;
        Object[] pre, post;                // each 3 values
        boolean smooth;
    }

    public static final class Clip {
        public final String name;
        public final float length;          // seconds, 0 = not timed
        public final boolean loop, hold;
        final Map<String, Map<String, Channel>> bones = new LinkedHashMap<>();
        Clip(String n, float len, boolean loop, boolean hold) { name = n; length = len; this.loop = loop; this.hold = hold; }
        public boolean affects(String bone) { return bones.containsKey(bone); }
    }

    private final Map<String, Clip> clips = new HashMap<>();

    public static BedrockAnim load(String resourcePath) {
        try (InputStream in = BedrockAnim.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("Missing animation " + resourcePath);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject anims = root.getAsJsonObject("animations");
            BedrockAnim ba = new BedrockAnim();
            for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
                String id = e.getKey();
                JsonObject a = e.getValue().getAsJsonObject();
                float len = a.has("animation_length") ? a.get("animation_length").getAsFloat() : 0f;
                boolean loop = false, hold = false;
                if (a.has("loop")) {
                    JsonElement l = a.get("loop");
                    if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) loop = l.getAsBoolean();
                    else if (l.isJsonPrimitive() && l.getAsString().equals("hold_on_last_frame")) hold = true;
                }
                Clip c = new Clip(id.startsWith(PREFIX) ? id.substring(PREFIX.length()) : id, len, loop, hold);
                if (a.has("bones")) {
                    for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                        Map<String, Channel> chs = new HashMap<>();
                        for (Map.Entry<String, JsonElement> ce : be.getValue().getAsJsonObject().entrySet()) {
                            Channel ch = channel(ce.getValue());
                            if (ch != null) chs.put(ce.getKey(), ch);
                        }
                        c.bones.put(be.getKey(), chs);
                    }
                }
                ba.clips.put(c.name, c);
            }
            return ba;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load " + resourcePath, ex);
        }
    }

    public Clip clip(String name) { return clips.get(name); }

    // ------------------------------------------------------------------ parsing
    private static Object[] vec(JsonElement e) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            Object[] r = new Object[3];
            for (int i = 0; i < 3; i++) r[i] = a.size() > i ? scalar(a.get(i)) : (Object) 0.0;
            return r;
        }
        Object s = scalar(e);                 // uniform scalar (e.g. scale 0.9)
        return new Object[]{s, s, s};
    }

    private static Object scalar(JsonElement e) {
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        return e.getAsString();
    }

    private static Channel channel(JsonElement e) {
        Channel ch = new Channel();
        if (e.isJsonArray() || e.isJsonPrimitive()) { ch.constant = vec(e); return ch; }
        JsonObject o = e.getAsJsonObject();
        ch.keys = new ArrayList<>();
        for (Map.Entry<String, JsonElement> ke : o.entrySet()) {
            Key k = new Key();
            k.time = Float.parseFloat(ke.getKey());
            JsonElement v = ke.getValue();
            if (v.isJsonObject()) {
                JsonObject vo = v.getAsJsonObject();
                if (vo.has("vector")) { k.pre = k.post = vec(vo.get("vector")); }
                else {
                    k.pre = vo.has("pre") ? vec(vo.get("pre")) : null;
                    k.post = vo.has("post") ? vec(vo.get("post")) : null;
                    if (k.pre == null) k.pre = k.post;
                    if (k.post == null) k.post = k.pre;
                }
                k.smooth = vo.has("lerp_mode") && vo.get("lerp_mode").getAsString().equals("catmullrom");
            } else {
                k.pre = k.post = vec(v);
            }
            ch.keys.add(k);
        }
        ch.keys.sort((a, b) -> Float.compare(a.time, b.time));
        return ch;
    }

    // ------------------------------------------------------------------ sampling
    /** Writes the pose of {@code clip} at {@code t} seconds into {@code out} (replacing those bones). */
    public static void sample(Clip clip, float t, Molang.Vars ctx, Map<String, DollModel.BoneAnim> out) {
        float tt = t;
        if (clip.length > 0) {
            if (clip.loop) tt = t % clip.length;
            else tt = Math.min(t, clip.length);
        }
        final float animTime = tt;
        Molang.Vars vars = name -> {
            switch (name) {
                case "q.anim_time": case "query.anim_time": return animTime;
                default: return ctx.get(name);
            }
        };
        for (Map.Entry<String, Map<String, Channel>> be : clip.bones.entrySet()) {
            String bone = be.getKey();
            if (bone.equals("item") || bone.equals("root")) continue;      // placement handled by the view settings
            DollModel.BoneAnim a = new DollModel.BoneAnim();
            for (Map.Entry<String, Channel> ce : be.getValue().entrySet()) {
                if (bone.equals("2pgdgld") && ce.getKey().equals("rotation")) continue;   // pack-specific hand turn
                double[] v = value(ce.getValue(), animTime, vars);
                switch (ce.getKey()) {
                    // Bedrock is left-handed: rotations are inverted; positions use the model's own axes
                    case "rotation": a.rx = (float) -v[0]; a.ry = (float) -v[1]; a.rz = (float) -v[2]; break;
                    case "position": a.tx = (float) v[0]; a.ty = (float) v[1]; a.tz = (float) v[2]; break;
                    case "scale": a.sx = (float) v[0]; a.sy = (float) v[1]; a.sz = (float) v[2]; break;
                    default: break;
                }
            }
            out.put(bone, a);
        }
    }

    private static double[] eval(Object[] o, Molang.Vars vars) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = o[i] instanceof Double ? (Double) o[i] : Molang.eval((String) o[i], vars);
        return r;
    }

    private static double[] value(Channel ch, float t, Molang.Vars vars) {
        if (ch.constant != null) return eval(ch.constant, vars);
        List<Key> ks = ch.keys;
        if (ks.isEmpty()) return new double[]{0, 0, 0};
        if (t <= ks.get(0).time) return eval(ks.get(0).pre, vars);
        Key last = ks.get(ks.size() - 1);
        if (t >= last.time) return eval(last.post, vars);
        int i = 0;
        while (i + 1 < ks.size() && ks.get(i + 1).time <= t) i++;
        Key k0 = ks.get(i), k1 = ks.get(i + 1);
        double f = (t - k0.time) / (k1.time - k0.time);
        double[] p1 = eval(k0.post, vars), p2 = eval(k1.pre, vars);
        double[] r = new double[3];
        if (k0.smooth || k1.smooth) {
            double[] p0 = eval((i > 0 ? ks.get(i - 1) : k0).post, vars);
            double[] p3 = eval((i + 2 < ks.size() ? ks.get(i + 2) : k1).pre, vars);
            for (int c = 0; c < 3; c++) {
                double f2 = f * f, f3 = f2 * f;
                r[c] = 0.5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * f + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * f2
                    + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * f3);
            }
        } else {
            for (int c = 0; c < 3; c++) r[c] = p1[c] + (p2[c] - p1[c]) * f;
        }
        return r;
    }
}
