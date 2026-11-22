package dev.totemdoll.client;

import dev.totemdoll.client.DollModel.BoneAnim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Chooses which of the pack's Bedrock animations plays:
 * idle breathing (loop) + one-shots: pop-in when the totem is taken in hand, arms-up jump, stretch while falling,
 * squash on landing, and random idle fidgets (head tilts, wave, shiver...).
 */
public final class DollAnimator {
    private static final String IDLE = "jmryog";
    private static final String EQUIP = "jvvabu";
    private static final String JUMP = "bpgepc";
    private static final String FALL = "jrnoca";
    private static final String LAND = "vvinnn";
    private static final String[] FIDGETS = {"fdgzhe", "gwsewn", "yjcpmk", "inhsns", "fjxbyn", "ziovgj"};
    private static final float BLEND = 0.15f;

    private final BedrockAnim anims;
    private final Random rng = new Random();
    private BedrockAnim.Clip clip;
    private float life, clipTime, blendT = 1f, airTime, idleTimer, nextFidget = 5f;
    private boolean wasAir, wasHolding, still;
    private Map<String, BoneAnim> pose = new HashMap<>();
    private Map<String, BoneAnim> blendFrom = new HashMap<>();

    public DollAnimator() {
        anims = BedrockAnim.load("/assets/totemdoll/bedrock/animations/doll.animation.json");
    }

    public void setStill(boolean still) { this.still = still; }

    /** Current pose for both the doll and the cup (bone names are unique across the models). */
    public Map<String, BoneAnim> pose() { return still ? new HashMap<>() : pose; }

    public void update(float dt, boolean onGround, double vy, boolean holding) {
        dt = Math.min(dt, 0.05f);
        life += dt;
        boolean air = !onGround;

        if (holding && !wasHolding) play(EQUIP, false);
        else if (!air && wasAir && airTime > 0.25f) play(LAND, false);
        else if (air) {
            airTime += dt;
            if (vy > 0.1 && !is(JUMP) && !is(FALL)) play(JUMP, false);
            else if (vy < -0.25 && airTime > 0.35f && !is(FALL)) play(FALL, false);
        }
        if (!air) airTime = 0f;
        wasAir = air;
        wasHolding = holding;

        if (clip != null) {
            clipTime += dt;
            boolean airClip = is(JUMP) || is(FALL);
            if (!airClip && clip.length > 0 && clipTime >= clip.length) endClip();
            else if (airClip && !air) endClip();
        }
        if (clip == null && onGround) {
            idleTimer += dt;
            if (idleTimer >= nextFidget) {
                play(FIDGETS[rng.nextInt(FIDGETS.length)], true);
            }
        }
        if (blendT < 1f) blendT = Math.min(1f, blendT + dt / BLEND);
        compute();
    }

    private boolean is(String name) { return clip != null && clip.name.equals(name); }

    private void play(String name, boolean fidget) {
        BedrockAnim.Clip c = anims.clip(name);
        if (c == null) return;
        blendFrom = copy(pose);
        blendT = 0f;
        clip = c;
        clipTime = 0f;
        idleTimer = 0f;
        nextFidget = 6f + rng.nextFloat() * 8f;
    }

    private void endClip() {
        blendFrom = copy(pose);
        blendT = 0f;
        clip = null;
        idleTimer = 0f;
        nextFidget = 6f + rng.nextFloat() * 8f;
    }

    private void compute() {
        Molang.Vars ctx = n -> {
            if (n.endsWith("life_time")) return life;
            if (n.endsWith("item_slot")) return "main_hand".hashCode();
            return 0.0;
        };
        Map<String, BoneAnim> p = new HashMap<>();
        BedrockAnim.sample(anims.clip(IDLE), life, ctx, p);
        if (clip != null) BedrockAnim.sample(clip, clipTime, ctx, p);
        if (blendT < 1f) {
            Set<String> names = new HashSet<>(p.keySet());
            names.addAll(blendFrom.keySet());
            for (String n : names) p.put(n, lerp(blendFrom.get(n), p.get(n), blendT));
        }
        pose = p;
    }

    private static Map<String, BoneAnim> copy(Map<String, BoneAnim> m) {
        Map<String, BoneAnim> r = new HashMap<>();
        for (Map.Entry<String, BoneAnim> e : m.entrySet()) r.put(e.getKey(), lerp(e.getValue(), e.getValue(), 1f));
        return r;
    }

    private static BoneAnim lerp(BoneAnim a, BoneAnim b, float t) {
        if (a == null) a = new BoneAnim();
        if (b == null) b = new BoneAnim();
        BoneAnim r = new BoneAnim();
        r.tx = a.tx + (b.tx - a.tx) * t; r.ty = a.ty + (b.ty - a.ty) * t; r.tz = a.tz + (b.tz - a.tz) * t;
        r.rx = a.rx + (b.rx - a.rx) * t; r.ry = a.ry + (b.ry - a.ry) * t; r.rz = a.rz + (b.rz - a.rz) * t;
        r.sx = a.sx + (b.sx - a.sx) * t; r.sy = a.sy + (b.sy - a.sy) * t; r.sz = a.sz + (b.sz - a.sz) * t;
        return r;
    }
}
