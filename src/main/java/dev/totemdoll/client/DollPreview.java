package dev.totemdoll.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live 3D preview of the doll for the settings screens: a tiny software rasteriser (orthographic, z-buffer,
 * nearest-neighbour textures) that works on the very same {@link DollModel} geometry and {@link DollAnimator}
 * poses as the in-game renderer. It does not use the GUI item atlas, so it is crisp, rotates (turntable) and
 * updates the moment a setting changes. No Minecraft classes: output goes through {@link Canvas}.
 */
public final class DollPreview {
    public interface Canvas { void fill(int x1, int y1, int x2, int y2, int argb); }

    /** id = "namespace:path/to/texture.png" */
    public interface Textures { PngMask get(String id); }

    public static final int RES = 128;                     // raster resolution (RES x RES cells)
    private static final String ALEX = "totemdoll:textures/entity/alex.png";
    private static final String STEVE = "totemdoll:textures/entity/steve.png";
    private static final String VANILLA = "minecraft:textures/item/totem_of_undying.png";
    private static final float[] LIGHT = normalize(-0.35f, 0.55f, 0.75f);

    private final Textures textures;
    private final DollAnimator anim = new DollAnimator();
    private final Map<String, DollModel> models = new HashMap<>();
    private final Map<String, Boolean> masked = new HashMap<>();
    private final int[] color = new int[RES * RES];
    private final float[] depth = new float[RES * RES];
    private float angle = 20f;
    private long last = System.nanoTime();
    private boolean first = true;

    public DollPreview(Textures textures) { this.textures = textures; }

    /** Turntable angle in degrees (0 = facing the viewer). */
    public void setAngle(float degrees) { this.angle = degrees; }

    private static final class Part {
        DollModel model; Map<String, DollModel.BoneAnim> pose; boolean layer3d; String tex; boolean flat;
    }

    private DollModel model(String name, String skinTex) {
        DollModel m = models.get(name);
        if (m == null) {
            m = DollModel.load("/assets/totemdoll/bedrock/models/" + name + ".geo.json");
            models.put(name, m);
        }
        if (skinTex != null && !Boolean.TRUE.equals(masked.get(name + skinTex))) {
            m.setMask(textures.get(skinTex));
            masked.put(name + skinTex, true);
        }
        return m;
    }

    /**
     * @param spin   turntable rotation
     * @param adjust optional position settings to apply (Adjust Doll Position preview)
     */
    public void render(Canvas canvas, boolean spin, DollConfig.View adjust) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        DollConfig cfg = DollConfig.get();
        anim.setStill(!cfg.animations);
        anim.setBlinkEnabled(cfg.blink);
        anim.setRestMode(!cfg.totem3d ? 0 : cfg.isFoot() ? 2 : 1);
        anim.update(dt, true, 0, !first, false, 20f);      // first frame "not held" -> the equip pop-in plays next
        first = false;
        if (spin) angle = (angle + dt * 38f) % 360f;

        java.util.Arrays.fill(color, 0);
        java.util.Arrays.fill(depth, -1e9f);
        List<Part> parts = new ArrayList<>();
        String skin = cfg.slim ? ALEX : STEVE;
        Map<String, DollModel.BoneAnim> pose = anim.pose();
        if (!cfg.enabled) {
            // vanilla totem sprite: a flat quad
            float[] q = {-8, 0, 0, 0, 1, 8, 0, 0, 1, 1, 8, 16, 0, 1, 0, -8, 16, 0, 0, 0};
            rasterQuad(q, VANILLA, 0f, 0.5f, adjust, angle, false, new float[]{0, 0, 1});
            flush(canvas);
            return;
        }
        if (!cfg.totem3d) {
            Part p = new Part();
            p.model = model("model2d", null); p.pose = pose; p.tex = skin; p.flat = true;
            parts.add(p);
        } else if (cfg.isFoot()) {
            Part d = new Part();
            d.model = model(cfg.slim ? "alex" : "steve", skin); d.pose = pose; d.layer3d = cfg.layer3d; d.tex = skin;
            parts.add(d);
            boolean wide = cfg.model.equals("foot_wide");
            Map<String, DollModel.BoneAnim> fm = new HashMap<>(pose);
            DollModel.BoneAnim fb = new DollModel.BoneAnim();
            fb.ry = 180f; fb.ty = 11.2f;
            fm.put("foot", fb);
            Part f = new Part();
            f.model = model(wide ? "foot_steve" : "foot_alex", null); f.pose = fm; f.tex = wide ? STEVE : ALEX;
            parts.add(f);
        } else {
            Part c = new Part();
            c.model = model(cfg.model, null); c.pose = pose; c.tex = "totemdoll:textures/entity/" + cfg.model + ".png";
            parts.add(c);
            Part d = new Part();
            d.model = model(cfg.slim ? "alex" : "steve", skin); d.pose = pose; d.layer3d = cfg.layer3d; d.tex = skin;
            parts.add(d);
        }
        for (Part p : parts) {
            final List<float[]> verts = new ArrayList<>();
            p.model.render(p.pose, p.layer3d, (x, y, z, u, v, nx, ny, nz) -> verts.add(new float[]{x, y, z, u, v, nx, ny, nz}));
            for (int i = 0; i + 3 < verts.size(); i += 4) {
                float[] q = new float[20];
                for (int k = 0; k < 4; k++) {
                    float[] s = verts.get(i + k);
                    q[k * 5] = s[0]; q[k * 5 + 1] = s[1]; q[k * 5 + 2] = s[2]; q[k * 5 + 3] = s[3]; q[k * 5 + 4] = s[4];
                }
                float[] n = verts.get(i);
                rasterQuad(q, p.tex, 1f, 0.5f, adjust, angle, !p.flat,
                    new float[]{n[5], n[6], n[7]}, p.flat);
            }
        }
        flush(canvas);
    }

    private void rasterQuad(float[] q, String tex, float scale, float unused, DollConfig.View adjust, float yaw,
                            boolean turn180, float[] normal) {
        rasterQuad(q, tex, scale, unused, adjust, yaw, turn180, normal, false);
    }

    private void rasterQuad(float[] q, String tex, float scale, float unused, DollConfig.View adjust, float yaw,
                            boolean turn180, float[] normal, boolean flat) {
        PngMask t = textures.get(tex);
        if (t == null) return;
        float[][] p = new float[4][3];
        for (int i = 0; i < 4; i++) {
            float x = q[i * 5], y = q[i * 5 + 1], z = q[i * 5 + 2];
            if (flat) { x = (x + 2f) * scale; y *= scale; z *= scale; }
            else if (turn180) { x = -x; z = -z; }
            p[i] = new float[]{x, y, z};
        }
        float[] n = normal.clone();
        if (!flat && turn180) { n[0] = -n[0]; n[2] = -n[2]; }
        if (adjust != null) {
            float[][] rot = rotZYX(adjust.rx, adjust.ry, adjust.rz);
            for (int i = 0; i < 4; i++) {
                float[] r = apply(rot, new float[]{p[i][0], (p[i][1] - 8f), p[i][2]});
                p[i] = new float[]{r[0] * adjust.scale + adjust.ox, r[1] * adjust.scale + 8f + adjust.oy, r[2] * adjust.scale + adjust.oz};
            }
            n = apply(rot, n);
        }
        if (yaw != 0f) {
            double a = Math.toRadians(yaw);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            for (int i = 0; i < 4; i++) {
                float x = p[i][0], z = p[i][2];
                p[i][0] = c * x + s * z;
                p[i][2] = -s * x + c * z;
            }
            float nx = c * n[0] + s * n[2], nz = -s * n[0] + c * n[2];
            n[0] = nx; n[2] = nz;
        }
        if (n[2] < 0f) { n[0] = -n[0]; n[1] = -n[1]; n[2] = -n[2]; }    // double-sided
        float shade = 0.55f + 0.45f * Math.max(0f, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
        float sc = RES * 0.84f / 20f;
        float[] sx = new float[4], sy = new float[4], sz = new float[4];
        for (int i = 0; i < 4; i++) {
            sx[i] = RES / 2f + p[i][0] * sc;
            sy[i] = RES * 0.5f - (p[i][1] - 8f) * sc;
            sz[i] = p[i][2];
        }
        tri(sx, sy, sz, q, 0, 1, 2, t, shade);
        tri(sx, sy, sz, q, 0, 2, 3, t, shade);
    }

    private void tri(float[] sx, float[] sy, float[] sz, float[] q, int a, int b, int c, PngMask t, float shade) {
        float x0 = sx[a], y0 = sy[a], x1 = sx[b], y1 = sy[b], x2 = sx[c], y2 = sy[c];
        float den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(den) < 1e-6f) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(RES - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(RES - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float l0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / den;
                float l1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / den;
                float l2 = 1f - l0 - l1;
                if (l0 < -1e-4f || l1 < -1e-4f || l2 < -1e-4f) continue;
                float z = l0 * sz[a] + l1 * sz[b] + l2 * sz[c];
                int idx = y * RES + x;
                if (z < depth[idx] - 1e-3f) continue;
                float u = l0 * q[a * 5 + 3] + l1 * q[b * 5 + 3] + l2 * q[c * 5 + 3];
                float v = l0 * q[a * 5 + 4] + l1 * q[b * 5 + 4] + l2 * q[c * 5 + 4];
                int tx = Math.max(0, Math.min(t.width - 1, (int) (u * t.width)));
                int ty = Math.max(0, Math.min(t.height - 1, (int) (v * t.height)));
                int argb = t.argb(tx, ty);
                if ((argb >>> 24) < 128) continue;
                int r = Math.min(255, (int) (((argb >> 16) & 255) * shade));
                int g = Math.min(255, (int) (((argb >> 8) & 255) * shade));
                int bl = Math.min(255, (int) ((argb & 255) * shade));
                color[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                depth[idx] = z;
            }
        }
    }

    /** Emits the raster as run-length merged rectangles (in raster cells; the caller scales them). */
    private void flush(Canvas c) {
        for (int y = 0; y < RES; y++) {
            int x = 0;
            while (x < RES) {
                int col = color[y * RES + x];
                if (col == 0) { x++; continue; }
                int e = x + 1;
                while (e < RES && color[y * RES + e] == col) e++;
                c.fill(x, y, e, y + 1, col);
                x = e;
            }
        }
    }

    // ---------------------------------------------------------------- small math
    private static float[] normalize(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static float[][] rotZYX(float xd, float yd, float zd) {
        double x = Math.toRadians(xd), y = Math.toRadians(yd), z = Math.toRadians(zd);
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x), cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        float[][] rx = {{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
        float[][] ry = {{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        float[][] rz = {{cz, -sz, 0}, {sz, cz, 0}, {0, 0, 1}};
        return mul(mul(rz, ry), rx);
    }

    private static float[][] mul(float[][] a, float[][] b) {
        float[][] r = new float[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    private static float[] apply(float[][] m, float[] v) {
        return new float[]{
            m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }
}
