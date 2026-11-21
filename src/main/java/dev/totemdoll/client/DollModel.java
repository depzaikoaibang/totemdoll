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

/** Minimal Bedrock .geo.json loader + quad emitter. No Minecraft classes, so it is easy to test. */
public final class DollModel {

    /** Receives one vertex. Positions are in model pixels (1/16 block), already posed. */
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    /** Per-bone animation offsets (pixels / degrees), added on top of the model pose. */
    public static final class BoneAnim {
        public float tx, ty, tz, rx, ry, rz;
    }

    private static final class Cube {
        float x0, y0, z0, x1, y1, z1;
        float[] pivot, rot;
        // per face: {u0, v0, u1, v1} in pixels, order NORTH,SOUTH,EAST,WEST,UP,DOWN
        float[][] uv = new float[6][];
    }

    private static final class Bone {
        String name, parent;
        float[] pivot = {0, 0, 0}, rot = {0, 0, 0};
        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
    }

    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();
    private float texW = 64, texH = 64;

    public static DollModel load(String resourcePath) {
        try (InputStream in = DollModel.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("Missing model " + resourcePath);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            DollModel m = new DollModel();
            JsonObject desc = geo.getAsJsonObject("description");
            if (desc.has("texture_width")) m.texW = desc.get("texture_width").getAsFloat();
            if (desc.has("texture_height")) m.texH = desc.get("texture_height").getAsFloat();
            for (JsonElement be : geo.getAsJsonArray("bones")) {
                JsonObject bo = be.getAsJsonObject();
                Bone b = new Bone();
                b.name = bo.get("name").getAsString();
                b.parent = bo.has("parent") ? bo.get("parent").getAsString() : null;
                if (bo.has("pivot")) b.pivot = floats(bo.getAsJsonArray("pivot"));
                if (bo.has("rotation")) b.rot = floats(bo.getAsJsonArray("rotation"));
                if (bo.has("cubes")) for (JsonElement ce : bo.getAsJsonArray("cubes")) b.cubes.add(cube(ce.getAsJsonObject()));
                m.bones.put(b.name, b);
            }
            for (Bone b : m.bones.values()) {
                Bone p = b.parent == null ? null : m.bones.get(b.parent);
                if (p == null) m.roots.add(b); else p.children.add(b);
            }
            return m;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + resourcePath, e);
        }
    }

    private static float[] floats(JsonArray a) {
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    private static Cube cube(JsonObject o) {
        Cube c = new Cube();
        float[] origin = floats(o.getAsJsonArray("origin"));
        float[] size = floats(o.getAsJsonArray("size"));
        float inf = o.has("inflate") ? o.get("inflate").getAsFloat() : 0f;
        c.x0 = origin[0] - inf; c.y0 = origin[1] - inf; c.z0 = origin[2] - inf;
        c.x1 = origin[0] + size[0] + inf; c.y1 = origin[1] + size[1] + inf; c.z1 = origin[2] + size[2] + inf;
        if (o.has("pivot")) c.pivot = floats(o.getAsJsonArray("pivot"));
        if (o.has("rotation")) c.rot = floats(o.getAsJsonArray("rotation"));
        JsonElement uv = o.get("uv");
        if (uv != null && uv.isJsonObject()) {
            JsonObject uvo = uv.getAsJsonObject();
            for (int i = 0; i < 6; i++) {
                if (!uvo.has(FACES[i])) continue;
                JsonObject f = uvo.getAsJsonObject(FACES[i]);
                float[] p = floats(f.getAsJsonArray("uv"));
                float[] s = f.has("uv_size") ? floats(f.getAsJsonArray("uv_size")) : new float[]{0, 0};
                c.uv[i] = new float[]{p[0], p[1], p[0] + s[0], p[1] + s[1]};
            }
        } else if (uv != null && uv.isJsonArray()) {          // classic box UV
            float[] p = floats(uv.getAsJsonArray());
            float sx = size[0], sy = size[1], sz = size[2];
            c.uv[0] = new float[]{p[0] + sz, p[1] + sz, p[0] + sz + sx, p[1] + sz + sy};
            c.uv[1] = new float[]{p[0] + sz + sx + sz, p[1] + sz, p[0] + sz + sx + sz + sx, p[1] + sz + sy};
            c.uv[2] = new float[]{p[0], p[1] + sz, p[0] + sz, p[1] + sz + sy};
            c.uv[3] = new float[]{p[0] + sz + sx, p[1] + sz, p[0] + sz + sx + sz, p[1] + sz + sy};
            c.uv[4] = new float[]{p[0] + sz + sx, p[1] + sz, p[0] + sz, p[1]};
            c.uv[5] = new float[]{p[0] + sz + sx + sx, p[1], p[0] + sz + sx, p[1] + sz};
        }
        return c;
    }

    public boolean hasBone(String n) { return bones.containsKey(n); }

    // ---------------------------------------------------------------- rendering

    public void render(Map<String, BoneAnim> anims, Sink sink) {
        for (Bone b : roots) renderBone(b, identity(), anims == null ? new HashMap<>() : anims, sink);
    }

    private void renderBone(Bone b, float[] parent, Map<String, BoneAnim> anims, Sink sink) {
        BoneAnim a = anims.get(b.name);
        float[] m = parent.clone();
        float px = b.pivot[0], py = b.pivot[1], pz = b.pivot[2];
        float tx = 0, ty = 0, tz = 0, rx = b.rot[0], ry = b.rot[1], rz = b.rot[2];
        if (a != null) { tx = a.tx; ty = a.ty; tz = a.tz; rx += a.rx; ry += a.ry; rz += a.rz; }
        m = mul(m, translation(px + tx, py + ty, pz + tz));
        m = mul(m, rotationZYX(rx, ry, rz));
        m = mul(m, translation(-px, -py, -pz));
        for (Cube c : b.cubes) {
            float[] cm = m;
            if (c.rot != null) {
                float[] cp = c.pivot != null ? c.pivot : new float[]{0, 0, 0};
                cm = mul(cm, translation(cp[0], cp[1], cp[2]));
                cm = mul(cm, rotationZYX(c.rot[0], c.rot[1], c.rot[2]));
                cm = mul(cm, translation(-cp[0], -cp[1], -cp[2]));
            }
            emitCube(c, cm, sink);
        }
        for (Bone ch : b.children) renderBone(ch, m, anims, sink);
    }

    private void emitCube(Cube c, float[] m, Sink sink) {
        float x0 = c.x0, y0 = c.y0, z0 = c.z0, x1 = c.x1, y1 = c.y1, z1 = c.z1;
        // corners per face seen from outside: top-left, top-right, bottom-right, bottom-left; plus outward normal
        float[][][] corners = {
            /*north*/ {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},
            /*south*/ {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},
            /*east */ {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},
            /*west */ {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},
            /*up   */ {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
            /*down */ {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}},
        };
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        for (int f = 0; f < 6; f++) {
            float[] uv = c.uv[f];
            if (uv == null) continue;
            float u0 = uv[0] / texW, v0 = uv[1] / texH, u1 = uv[2] / texW, v1 = uv[3] / texH;
            float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            float[] n = dir(m, normals[f]);
            for (int i = 0; i < 4; i++) {
                float[] p = pos(m, corners[f][i]);
                sink.vertex(p[0], p[1], p[2], uvs[i][0], uvs[i][1], n[0], n[1], n[2]);
            }
        }
    }

    // ---------------------------------------------------------------- tiny 4x4 math (column-major)

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static float[] translation(float x, float y, float z) {
        float[] m = identity(); m[12] = x; m[13] = y; m[14] = z; return m;
    }

    /** Rotation = Rz * Ry * Rx, angles in degrees (Bedrock order). */
    private static float[] rotationZYX(float xd, float yd, float zd) {
        double x = Math.toRadians(xd), y = Math.toRadians(yd), z = Math.toRadians(zd);
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x);
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        float[] rx = {1, 0, 0, 0, 0, cx, sx, 0, 0, -sx, cx, 0, 0, 0, 0, 1};
        float[] ry = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};
        float[] rz = {cz, sz, 0, 0, -sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        return mul(mul(rz, ry), rx);
    }

    private static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[col * 4 + k];
                r[col * 4 + row] = s;
            }
        return r;
    }

    private static float[] pos(float[] m, float[] p) {
        return new float[]{
            m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12],
            m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13],
            m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]};
    }

    private static float[] dir(float[] m, float[] d) {
        float x = m[0] * d[0] + m[4] * d[1] + m[8] * d[2];
        float y = m[1] * d[0] + m[5] * d[1] + m[9] * d[2];
        float z = m[2] * d[0] + m[6] * d[1] + m[10] * d[2];
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{x / l, y / l, z / l};
    }
}
