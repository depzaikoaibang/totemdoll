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

/**
 * Minimal Bedrock .geo.json loader + quad emitter. No Minecraft classes, so it is easy to test.
 * Supports an optional "3D skin layer": cubes with inflate > 0 (the outer skin layer) are replaced by
 * one small extruded voxel per opaque texel, like the 3D Skin Layers mod.
 */
public final class DollModel {

    /** Receives one vertex. Positions are in model pixels (1/16 block), already posed. */
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    /** Per-bone animation offsets (pixels / degrees / scale), applied on top of the model pose. */
    public static final class BoneAnim {
        public float tx, ty, tz, rx, ry, rz;
        public float sx = 1f, sy = 1f, sz = 1f;
        public boolean hidden;
    }

    private static final class Cube {
        float x0, y0, z0, x1, y1, z1;      // inflated extents
        float inflate;
        boolean invertY;
        float[] pivot, rot;
        // per face: {u0, v0, u1, v1} in pixels, order NORTH,SOUTH,EAST,WEST,UP,DOWN
        float[][] uv = new float[6][];
        List<float[]> voxels;              // lazily built: 23 floats per quad (4 x xyzuv + normal)
    }

    private static final class Bone {
        String name, parent;
        float[] pivot = {0, 0, 0}, rot = {0, 0, 0};
        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
    }

    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};
    private static final float VOXEL_DEPTH = 0.5f;

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();
    private float texW = 64, texH = 64;
    private PngMask mask;

    public static DollModel load(String resourcePath) {
        return load(resourcePath, null);
    }

    /** @param maskPath classpath PNG whose alpha decides which texels of the outer layer become voxels (or null) */
    public static DollModel load(String resourcePath, String maskPath) {
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
            if (maskPath != null) {
                try (InputStream mi = DollModel.class.getResourceAsStream(maskPath)) {
                    if (mi != null) m.mask = PngMask.load(mi);
                } catch (Exception ignored) {
                    m.mask = null;               // fall back to the plain inflated layer
                }
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
        c.inflate = inf;
        c.invertY = size[1] < 0f;
        float endX = origin[0] + size[0];
        float endY = origin[1] + size[1];
        float endZ = origin[2] + size[2];
        c.x0 = Math.min(origin[0], endX) - inf;
        c.y0 = Math.min(origin[1], endY) - inf;
        c.z0 = Math.min(origin[2], endZ) - inf;
        c.x1 = Math.max(origin[0], endX) + inf;
        c.y1 = Math.max(origin[1], endY) + inf;
        c.z1 = Math.max(origin[2], endZ) + inf;
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
            float sx = Math.abs(size[0]), sy = Math.abs(size[1]), sz = Math.abs(size[2]);
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

    /** Opacity mask of the skin texture, used by the 3D skin layer. */
    public void setMask(PngMask mask) { this.mask = mask; }

    // ---------------------------------------------------------------- rendering

    public void render(Map<String, BoneAnim> anims, Sink sink) {
        render(anims, false, sink);
    }

    public void render(Map<String, BoneAnim> anims, boolean layer3d, Sink sink) {
        boolean voxel = layer3d && mask != null;
        for (Bone b : roots) renderBone(b, identity(), anims == null ? new HashMap<>() : anims, voxel, sink);
    }

    private void renderBone(Bone b, float[] parent, Map<String, BoneAnim> anims, boolean voxel, Sink sink) {
        BoneAnim a = anims.get(b.name);
        if (a != null && a.hidden) return;
        float[] m = parent.clone();
        float px = b.pivot[0], py = b.pivot[1], pz = b.pivot[2];
        float tx = 0, ty = 0, tz = 0, rx = b.rot[0], ry = b.rot[1], rz = b.rot[2];
        float sx = 1, sy = 1, sz = 1;
        if (a != null) { tx = a.tx; ty = a.ty; tz = a.tz; rx += a.rx; ry += a.ry; rz += a.rz; sx = a.sx; sy = a.sy; sz = a.sz; }
        m = mul(m, translation(px + tx, py + ty, pz + tz));
        m = mul(m, rotationZYX(rx, ry, rz));
        if (sx != 1f || sy != 1f || sz != 1f) m = mul(m, scaling(sx, sy, sz));
        m = mul(m, translation(-px, -py, -pz));
        for (Cube c : b.cubes) {
            float[] cm = m;
            if (c.rot != null) {
                float[] cp = c.pivot != null ? c.pivot : new float[]{0, 0, 0};
                cm = mul(cm, translation(cp[0], cp[1], cp[2]));
                cm = mul(cm, rotationZYX(c.rot[0], c.rot[1], c.rot[2]));
                cm = mul(cm, translation(-cp[0], -cp[1], -cp[2]));
            }
            if (voxel && c.inflate > 0f) emitVoxels(c, cm, sink);
            else emitCube(c, cm, sink);
        }
        for (Bone ch : b.children) renderBone(ch, m, anims, voxel, sink);
    }

    /** Corner order per face seen from outside: top-left, top-right, bottom-right, bottom-left. */
    private static float[][][] faceCorners(float x0, float y0, float z0, float x1, float y1, float z1) {
        return new float[][][]{
            /*north*/ {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},
            /*south*/ {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},
            /*east */ {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},
            /*west */ {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},
            /*up   */ {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
            /*down */ {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}},
        };
    }

    private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private void emitCube(Cube c, float[] m, Sink sink) {
        float[][][] corners = faceCorners(c.x0, c.y0, c.z0, c.x1, c.y1, c.z1);
        for (int f = 0; f < 6; f++) {
            float[] uv = c.uv[f];
            if (uv == null) continue;
            float u0 = uv[0] / texW, v0 = uv[1] / texH, u1 = uv[2] / texW, v1 = uv[3] / texH;
            float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            float[] baseN = NORMALS[f];
            float[] n = dir(m, c.invertY ? new float[]{-baseN[0], -baseN[1], -baseN[2]} : baseN);
            for (int i = 0; i < 4; i++) {
                float[] p = pos(m, corners[f][i]);
                sink.vertex(p[0], p[1], p[2], uvs[i][0], uvs[i][1], n[0], n[1], n[2]);
            }
        }
    }

    // ---------------------------------------------------------------- 3D skin layer

    private void emitVoxels(Cube c, float[] m, Sink sink) {
        if (c.voxels == null) c.voxels = buildVoxels(c);
        for (float[] q : c.voxels) {
            float[] n = dir(m, new float[]{q[20], q[21], q[22]});
            for (int i = 0; i < 4; i++) {
                float[] p = pos(m, new float[]{q[i * 5], q[i * 5 + 1], q[i * 5 + 2]});
                sink.vertex(p[0], p[1], p[2], q[i * 5 + 3], q[i * 5 + 4], n[0], n[1], n[2]);
            }
        }
    }

    private static void lerp3(float[] a, float[] b, float t, float[] out) {
        for (int i = 0; i < 3; i++) out[i] = a[i] + (b[i] - a[i]) * t;
    }

    private List<float[]> buildVoxels(Cube c) {
        List<float[]> out = new ArrayList<>();
        float inf = c.inflate;
        // start at the un-inflated surface, extrude outwards
        float[][][] corners = faceCorners(c.x0 + inf, c.y0 + inf, c.z0 + inf, c.x1 - inf, c.y1 - inf, c.z1 - inf);
        for (int f = 0; f < 6; f++) {
            float[] uv = c.uv[f];
            if (uv == null) continue;
            int tw = Math.round(Math.abs(uv[2] - uv[0])), th = Math.round(Math.abs(uv[3] - uv[1]));
            if (tw == 0 || th == 0) continue;
            boolean flipU = uv[2] < uv[0], flipV = uv[3] < uv[1];
            float[] n = NORMALS[f];
            float[][] cr = corners[f];                    // TL, TR, BR, BL
            boolean[][] op = new boolean[tw][th];
            int[][] texX = new int[tw][th], texY = new int[tw][th];
            for (int i = 0; i < tw; i++) {
                for (int j = 0; j < th; j++) {
                    int tx = flipU ? (int) uv[0] - i - 1 : (int) uv[0] + i;
                    int ty = flipV ? (int) uv[1] - j - 1 : (int) uv[1] + j;
                    texX[i][j] = tx;
                    texY[i][j] = ty;
                    op[i][j] = mask.opaque(tx, ty);
                }
            }
            for (int i = 0; i < tw; i++) {
                for (int j = 0; j < th; j++) {
                    if (!op[i][j]) continue;
                    float a0 = i / (float) tw, a1 = (i + 1) / (float) tw, b0 = j / (float) th, b1 = (j + 1) / (float) th;
                    float[] tl = pt(cr, a0, b0), tr = pt(cr, a1, b0), br = pt(cr, a1, b1), bl = pt(cr, a0, b1);
                    float[] otl = up(tl, n), otr = up(tr, n), obr = up(br, n), obl = up(bl, n);
                    int tx = texX[i][j], ty = texY[i][j];
                    float ul = (flipU ? tx + 1 : tx) / texW, ur = (flipU ? tx : tx + 1) / texW;
                    float vt = (flipV ? ty + 1 : ty) / texH, vb = (flipV ? ty : ty + 1) / texH;
                    out.add(quad(otl, otr, obr, obl, ul, vt, ur, vt, ur, vb, ul, vb, n));
                    float uc = (tx + 0.5f) / texW, vc = (ty + 0.5f) / texH;
                    if (i == 0 || !op[i - 1][j]) out.add(side(otl, tl, bl, obl, uc, vc, sub(tl, tr)));
                    if (i == tw - 1 || !op[i + 1][j]) out.add(side(otr, obr, br, tr, uc, vc, sub(tr, tl)));
                    if (j == 0 || !op[i][j - 1]) out.add(side(otl, otr, tr, tl, uc, vc, sub(tl, bl)));
                    if (j == th - 1 || !op[i][j + 1]) out.add(side(obl, bl, br, obr, uc, vc, sub(bl, tl)));
                }
            }
        }
        return out;
    }

    private static float[] pt(float[][] cr, float a, float b) {
        float[] top = new float[3], bot = new float[3], r = new float[3];
        lerp3(cr[0], cr[1], a, top);
        lerp3(cr[3], cr[2], a, bot);
        lerp3(top, bot, b, r);
        return r;
    }

    private static float[] up(float[] p, float[] n) {
        return new float[]{p[0] + n[0] * VOXEL_DEPTH, p[1] + n[1] * VOXEL_DEPTH, p[2] + n[2] * VOXEL_DEPTH};
    }

    private static float[] sub(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{x / l, y / l, z / l};
    }

    private static float[] side(float[] a, float[] b, float[] c, float[] d, float u, float v, float[] n) {
        return quad(a, b, c, d, u, v, u, v, u, v, u, v, n);
    }

    private static float[] quad(float[] a, float[] b, float[] c, float[] d,
                                float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd, float[] n) {
        float[] q = new float[23];
        float[][] ps = {a, b, c, d};
        float[] us = {ua, ub, uc, ud}, vs = {va, vb, vc, vd};
        for (int i = 0; i < 4; i++) {
            q[i * 5] = ps[i][0]; q[i * 5 + 1] = ps[i][1]; q[i * 5 + 2] = ps[i][2];
            q[i * 5 + 3] = us[i]; q[i * 5 + 4] = vs[i];
        }
        q[20] = n[0]; q[21] = n[1]; q[22] = n[2];
        return q;
    }

    // ---------------------------------------------------------------- tiny 4x4 math (column-major)

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static float[] scaling(float x, float y, float z) {
        float[] m = identity(); m[0] = x; m[5] = y; m[10] = z; return m;
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
