package dev.totemdoll.client;

import dev.totemdoll.client.DollModel.BoneAnim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Port of the Bedrock pack's animation controllers.
 * Layers add up like in Bedrock (rotation / position summed, scale multiplied):
 *  - bob (idle breathing) and idle sway, always on
 *  - kedip (blink timeline of the original pack)
 *  - fall controller: falling (jrnoca) -> landing squash (vvinnn)
 *  - fidget controller: random head tilts / wave / cheer while standing still
 *  - equip pop-in (jvvabu), hurt (ziovgj) and the liquid ripple on landing (llaman)
 */
public final class DollAnimator {
    private static final String BOB = "jmryog", IDLE = "tuqbfp", KEDIP = "new";
    private static final String FALL = "jrnoca", LAND = "vvinnn", EQUIP = "jvvabu", HURT = "ziovgj", RIPPLE = "llaman";
    private static final String HELLO = "yjcpmk";
    /** Only drops of at least this many blocks get the falling pose, the water splash and the "hello" wave. */
    private static final float HIGH_FALL = 3.0f;
    private static final float JUMP_KICK = 20f, LAND_KICK = 26f, SPR_K = 240f, SPR_C = 11f;
    /** Fidget states of the original controller (index = Math.mod(kkvcsy + random(1..4), 5)). */
    private static final String[][] FIDGETS = {{"fdgzhe"}, {"gwsewn"}, {"yjcpmk", "inhsns"}, {"fjxbyn"}, {"bpgepc"}};
    private static final float REST_ARM_X = 78f, REST_ARM_Y = 12f;      // degrees (model space), tune to taste
    private static final float THIGH_ARM_X = 40f, THIGH_ARM_Y = 8f;
    private static final float[] FADE_T = {0f, 0.0556f, 0.1111f, 0.1667f, 0.2222f, 0.2778f, 0.3333f, 0.3889f, 0.4444f, 0.5f};
    private static final float[] FADE_W = {1f, 0.99451f, 0.9561f, 0.85185f, 0.64883f, 0.35117f, 0.14815f, 0.0439f, 0.00549f, 0f};

    private static final class Layer {
        final BedrockAnim.Clip clip;
        float time, fadeIn, maxWeight = 1f, fadeOut = -1f, fadeOutT;
        boolean curveFade, hermite, oneShot;

        Layer(BedrockAnim.Clip c) { clip = c; }

        float weight() {
            float w = maxWeight;
            if (fadeIn > 0f) w *= Math.min(1f, time / fadeIn);
            if (hermite) {
                float x = clamp(time, 0f, 1f) - clamp((time - 2f) / 2f, 0f, 1f);
                w *= x * x * (3f - 2f * x);
            }
            if (fadeOut >= 0f) {
                float t = Math.min(fadeOutT, fadeOut);
                if (curveFade) w *= curve(t / fadeOut * 0.5f);
                else w *= 1f - t / fadeOut;
            }
            return w;
        }

        boolean dead() {
            if (fadeOut >= 0f && fadeOutT >= fadeOut) return true;
            return oneShot && clip.length > 0f && time >= clip.length;
        }
    }

    private final BedrockAnim anims;
    private final Random rng = new Random();
    private final List<Layer> layers = new ArrayList<>();
    private float life;
    private boolean wasHolding, wasAir, still, blinkEnabled = true;
    private int restMode;                 // 0 none, 1 hands on the cup rim, 2 hands on the thighs (Foot models)
    private float airTime, lastAirTime, rippleCooldown, restWeight = 1f;
    private float fallDist, hs, hv, jumpAmt, helloTimer = -1f;   // hs / hv: spring of the head bob
    private boolean wasGround = true, wasInWater;
    private Map<String, BoneAnim> pose = new HashMap<>();
    private float blink;

    // fall controller
    private int fallState;                 // 0 default, 1 fall, 2 land
    private float fallTime;
    private Layer fallLayer, landLayer;
    // fidget controller
    private int fidState = -1, kk;
    private float fidTime, hevgci = 1f + new Random().nextFloat() * 11f;
    private boolean ghuiex;
    private final List<Layer> fidLayers = new ArrayList<>();
    // equip / hurt
    private Layer hurtLayer;
    private float lastHealth = -1f;

    public DollAnimator() {
        anims = BedrockAnim.load("/assets/totemdoll/bedrock/animations/doll.animation.json");
    }

    public void setStill(boolean still) { this.still = still; }

    public void setBlinkEnabled(boolean on) { this.blinkEnabled = on; }

    /**
     * Idle pose of the arms, kept ALL the time (only the animations that move the arms - falling, waving, cheering,
     * hurt, equip - temporarily take over): 1 = hands on the cup rim, 2 = hands on the thighs (Foot models).
     */
    public void setRestMode(int mode) { this.restMode = mode; }

    /** Current pose for every bone of every model (bone names are shared between the models). */
    public Map<String, BoneAnim> pose() {
        if (!still) return pose;
        Map<String, BoneAnim> m = new HashMap<>();       // animations off: the doll can still blink
        if (blinkEnabled) {
            Map<String, BoneAnim> k = new HashMap<>();
            add(k, KEDIP, life, 1f);
            BoneAnim kb = k.get("kedip");
            if (kb != null) m.put("kedip", kb);
            BoneAnim h = new BoneAnim();
            h.blink = blinkOf(kb);
            m.put("2pg_head", h);
        }
        return m;
    }

    // ------------------------------------------------------------------ update
    public void update(float dt, boolean onGround, double vy, boolean holding, boolean inWater, float health) {
        dt = Math.min(dt, 0.05f);
        life += dt;
        float vSpeed = onGround ? 0f : (float) (vy * 20.0);          // Bedrock: blocks per second
        boolean fastFall = vSpeed < -8f && !inWater;

        if (holding && !wasHolding) startEquip();
        wasHolding = holding;

        if (lastHealth >= 0f && health < lastHealth - 1e-3f && (hurtLayer == null || hurtLayer.time > 0.49f)) startHurt();
        lastHealth = health;

        boolean air = !onGround;
        if (air && vy < 0) fallDist += (float) (-vy * dt * 20.0);          // blocks fallen since leaving the ground
        if (onGround) {
            if (wasAir) lastAirTime = airTime;
            airTime = 0f;
        } else {
            airTime += dt;
        }
        rippleCooldown = Math.max(0f, rippleCooldown - dt);

        boolean landed = wasAir && onGround;
        boolean splashed = !wasInWater && inWater;
        if (wasGround && air && vy > 0.05) hv += JUMP_KICK;                // take-off: the head pops up
        if (landed || splashed) {
            if (fallDist >= HIGH_FALL) impact();                           // from high up: squash + splash + hello
            else if (landed && lastAirTime > 0.2f) {                       // small hop: the head just slams down
                hv -= LAND_KICK * Math.min(1f, 0.4f + fallDist / HIGH_FALL);
            }
        }
        if (onGround || inWater) fallDist = 0f;

        updateFall(dt, onGround, vSpeed, inWater);

        float acc = -SPR_K * hs - SPR_C * hv;                              // damped spring of the head bob
        hv += acc * dt;
        hs = Math.max(-2.5f, Math.min(2.5f, hs + hv * dt));
        float smallAir = (air && !inWater && fallDist < HIGH_FALL) ? 1f : 0f;
        jumpAmt += (smallAir - jumpAmt) * (1f - (float) Math.exp(-dt * 12f));
        wasAir = air;
        wasGround = onGround;
        wasInWater = inWater;

        if (helloTimer >= 0f) {
            helloTimer -= dt;
            if (helloTimer < 0f && (onGround || inWater)) {
                if (fidState >= 0) endFidget();
                startFidget(2);                                            // "hello" after a big landing
            }
        }
        updateFidget(dt, onGround, fastFall, inWater);

        for (Layer l : layers) {
            l.time += dt;
            if (l.fadeOut >= 0f) l.fadeOutT += dt;
        }
        layers.removeIf(Layer::dead);
        if (fallLayer != null && !layers.contains(fallLayer)) fallLayer = null;
        if (landLayer != null && !layers.contains(landLayer)) landLayer = null;
        if (hurtLayer != null && !layers.contains(hurtLayer)) hurtLayer = null;
        fidLayers.removeIf(l -> !layers.contains(l));
        compute();
    }

    private Layer layer(String clipName, boolean oneShot) {
        BedrockAnim.Clip c = anims.clip(clipName);
        if (c == null) return null;
        Layer l = new Layer(c);
        l.oneShot = oneShot;
        return l;
    }

    private void fadeOut(Layer l, float seconds, boolean curve) {
        if (l == null || l.fadeOut >= 0f) return;
        l.fadeOut = seconds;
        l.fadeOutT = 0f;
        l.curveFade = curve;
        l.oneShot = false;
    }

    private void updateFall(float dt, boolean onGround, float vSpeed, boolean inWater) {
        switch (fallState) {
            case 0:
                if (!onGround && !inWater && fallDist > HIGH_FALL) enterFall();   // only after falling a few blocks
                break;
            case 1:
                fallTime += dt;
                if (onGround || inWater || vSpeed >= -0.15f) {
                    fadeOut(fallLayer, 0.12f, false);
                    fallState = 0;
                }
                break;
            default:
                if (landLayer == null) fallState = 0;
                else if (!onGround && !inWater && fallDist > HIGH_FALL) enterFall();
                break;
        }
    }

    /** Big landing on the ground or a splash into water: squash, the pack's water splash, then a "hello". */
    private void impact() {
        enterLand(1f);
        if (rippleCooldown <= 0f) {
            Layer r = layer(RIPPLE, true);
            if (r != null) {
                for (Layer old : new ArrayList<>(layers)) if (old.clip == r.clip) layers.remove(old);
                layers.add(r);
                rippleCooldown = 1.0f;
            }
        }
        helloTimer = 0.55f;
    }

    private void enterFall() {
        fadeOut(landLayer, 0.06f, false);
        Layer l = layer(FALL, false);
        if (l != null) { l.fadeIn = 0.06f; layers.add(l); }
        fallLayer = l;
        fallState = 1;
        fallTime = 0f;
    }

    private void enterLand(float weight) {
        fadeOut(fallLayer, 0.16f, false);
        Layer l = layer(LAND, true);
        if (l != null) { l.fadeIn = 0.16f; l.maxWeight = weight; layers.add(l); }
        landLayer = l;
        fallState = 2;
    }

    private void updateFidget(float dt, boolean onGround, boolean fastFall, boolean inWater) {
        fidTime += dt;
        if (fidState < 0) {
            if (!ghuiex && onGround && fidTime > 1.375f) startFidget(2);
            else if (onGround && fidTime > hevgci && !fastFall) {
                kk = (kk + 1 + rng.nextInt(4)) % 5;
                startFidget(kk);
            }
        } else {
            boolean finished = fidState == 3 ? fidTime > 4f : fidTime >= fidgetLength(fidState);
            if (finished || fastFall || inWater) endFidget();
        }
    }

    private float fidgetLength(int idx) {
        float len = 0f;
        for (String n : FIDGETS[idx]) {
            BedrockAnim.Clip c = anims.clip(n);
            if (c != null) len = len == 0f ? c.length : Math.min(len, c.length);
        }
        return len;
    }

    private void startFidget(int idx) {
        fidState = idx;
        fidTime = 0f;
        for (String n : FIDGETS[idx]) {
            Layer l = layer(n, false);
            if (l == null) continue;
            l.hermite = idx == 3;
            layers.add(l);
            fidLayers.add(l);
        }
        if (idx == 2) ghuiex = true;
    }

    private void endFidget() {
        for (Layer l : fidLayers) fadeOut(l, 0.5f, true);
        fidLayers.clear();
        fidState = -1;
        fidTime = 0f;
        hevgci = 1f + rng.nextFloat() * 11f;
    }

    private void startEquip() {
        for (Layer l : new ArrayList<>(fidLayers)) layers.remove(l);
        fidLayers.clear();
        fidState = -1;
        fidTime = 0f;
        ghuiex = false;
        Layer l = layer(EQUIP, true);
        if (l != null) layers.add(l);
    }

    private void startHurt() {
        if (hurtLayer != null) layers.remove(hurtLayer);
        hurtLayer = layer(HURT, true);
        if (hurtLayer != null) layers.add(hurtLayer);
    }

    // ------------------------------------------------------------------ pose
    private void compute() {
        Map<String, BoneAnim> p = new HashMap<>();
        add(p, BOB, life, 1f);
        add(p, IDLE, life, 1f);
        add(p, KEDIP, life, 1f);
        for (Layer l : layers) {
            float w = l.weight();
            if (w > 0.001f) add(p, l.clip.name, l.time, w, !armsFree(l.clip.name));
        }
        applyRest(p);
        applyJump(p);
        BoneAnim k = p.get("kedip");
        blink = blinkEnabled ? blinkOf(k) : 0f;
        if (!blinkEnabled && k != null) k.tz = 0.1f;                  // keep the 2D model's eyes open
        BoneAnim head = p.computeIfAbsent("2pg_head", n -> new BoneAnim());
        head.blink = blink;
        pose = p;
    }

    /**
     * Resting arm pose, applied all the time. Only layers that really move the arms (fall, wave 'hello', cheer, hurt,
     * equip...) fade it out; head-tilt fidgets and the ripple do not, so the hands stay on the cup / thighs.
     */
    private void applyRest(Map<String, BoneAnim> p) {
        float armActivity = 0f;
        for (Layer l : layers) if (armsFree(l.clip.name)) armActivity = Math.max(armActivity, l.weight());
        // exact complement of the animation's weight: the arms glide between the two without passing the hanging pose
        restWeight = restMode != 0 && !still ? 1f - clamp(armActivity, 0f, 1f) : 0f;
        if (restWeight < 0.002f) return;
        float w = restWeight;
        float ax, ay, sink;
        if (restMode == 2) { ax = THIGH_ARM_X; ay = THIGH_ARM_Y; sink = 0f; }        // hands on the thighs
        else { ax = REST_ARM_X; ay = REST_ARM_Y; sink = -1.8f; }                       // hands on the cup rim
        ax -= (restMode == 2 ? 8f : 14f) * jumpAmt;                                    // jumping: hands only dip a little
        BoneAnim root = p.computeIfAbsent("2pgdgld", n -> new BoneAnim());
        root.ty += sink * w;
        BoneAnim ra = p.computeIfAbsent("2pg_rightarm", n -> new BoneAnim());
        ra.rx += ax * w; ra.ry += ay * w;
        BoneAnim la = p.computeIfAbsent("2pg_leftarm", n -> new BoneAnim());
        la.rx += ax * w; la.ry -= ay * w;
    }

    /** Head pops up when jumping, then drops down like a spring (also a small slam on landing). */
    private void applyJump(Map<String, BoneAnim> p) {
        if (still || Math.abs(hs) < 0.001f) return;
        BoneAnim head = p.computeIfAbsent("2pg_head", n -> new BoneAnim());
        head.ty += hs;
        BoneAnim root = p.computeIfAbsent("2pgdgld", n -> new BoneAnim());
        root.ty += hs * 0.35f;
    }

    /** For tests / debugging: names of the clips currently playing. */
    public String active() {
        StringBuilder sb = new StringBuilder();
        for (Layer l : layers) sb.append(l.clip.name).append(' ');
        return sb.toString().trim();
    }

    /** The pack's kedip bone sits at z=+0.1 (hidden) and slides to z=-0.02 while the eyes are closed. */
    private static float blinkOf(BoneAnim kedip) {
        if (kedip == null) return 0f;
        return clamp((0.1f - kedip.tz) / 0.12f, 0f, 1f);
    }

    private void add(Map<String, BoneAnim> out, String clipName, float time, float weight) {
        add(out, clipName, time, weight, false);
    }

    /** Only the falling pose and the "hello" wave are allowed to take the arms away from their resting place. */
    private static boolean armsFree(String clip) {
        return clip.equals(FALL) || clip.equals(HELLO);
    }

    private void add(Map<String, BoneAnim> out, String clipName, float time, float weight, boolean maskArms) {
        BedrockAnim.Clip c = anims.clip(clipName);
        if (c == null) return;
        Map<String, BoneAnim> tmp = new HashMap<>();
        BedrockAnim.sample(c, time, ctx(), tmp);
        for (Map.Entry<String, BoneAnim> e : tmp.entrySet()) {
            if (maskArms && (e.getKey().equals("2pg_rightarm") || e.getKey().equals("2pg_leftarm"))) continue;
            BoneAnim a = e.getValue(), r = out.computeIfAbsent(e.getKey(), n -> new BoneAnim());
            r.rx += a.rx * weight; r.ry += a.ry * weight; r.rz += a.rz * weight;
            r.tx += a.tx * weight; r.ty += a.ty * weight; r.tz += a.tz * weight;
            r.sx *= 1f + (a.sx - 1f) * weight;
            r.sy *= 1f + (a.sy - 1f) * weight;
            r.sz *= 1f + (a.sz - 1f) * weight;
        }
    }

    private Molang.Vars ctx() {
        final float t = life;
        return n -> {
            if (n.endsWith("life_time")) return t;
            if (n.endsWith("item_slot")) return "main_hand".hashCode();
            return 0.0;
        };
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float curve(float t) {                // t in 0..0.5 seconds of the controller's fade-out table
        if (t <= 0f) return 1f;
        for (int i = 1; i < FADE_T.length; i++) {
            if (t <= FADE_T[i]) {
                float f = (t - FADE_T[i - 1]) / (FADE_T[i] - FADE_T[i - 1]);
                return FADE_W[i - 1] + (FADE_W[i] - FADE_W[i - 1]) * f;
            }
        }
        return 0f;
    }
}
