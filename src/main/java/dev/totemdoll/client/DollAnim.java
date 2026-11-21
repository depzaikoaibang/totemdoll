package dev.totemdoll.client;

import dev.totemdoll.client.DollModel.BoneAnim;

import java.util.HashMap;
import java.util.Map;

/**
 * Procedural animation: idle bob, jump (rising), fall, landing squash.
 * All tunables are constants at the top so you can adjust the feel / signs of rotations.
 */
public final class DollAnim {
    // ---- tunables -------------------------------------------------------
    static final float ARM_SIGN = 1f;          // flip to -1 if arms swing the wrong way
    static final float JUMP_RISE_PX = 1.8f;    // doll lifts out of the cup when jumping
    static final float FALL_RISE_PX = 2.2f;    // doll floats up while falling
    static final float JUMP_ARM_DEG = 35f;
    static final float FALL_ARM_DEG = 140f;
    static final float LAND_STIFFNESS = 300f, LAND_DAMPING = 14f;

    private float time, up, down, spring, springVel, lastVy;
    private boolean wasAir;

    /** vy = vertical velocity in blocks/tick (player.getDeltaMovement().y). */
    public void update(float dt, boolean onGround, double vy) {
        dt = Math.min(dt, 0.05f);
        time += dt;
        boolean air = !onGround;
        float upTarget = air && vy > 0.05 ? 1f : 0f;
        float downTarget = air && vy < -0.1 ? (float) Math.min(1.0, Math.max(0.35, -vy / 0.8)) : 0f;
        float k = 1f - (float) Math.exp(-dt * 12f);
        up += (upTarget - up) * k;
        down += (downTarget - down) * k;
        if (air) lastVy = (float) vy;
        if (wasAir && onGround) {                         // landed: kick the spring
            springVel += Math.min(1.2f, Math.max(0.15f, -lastVy)) * 45f;
        }
        wasAir = air;
        float acc = -LAND_STIFFNESS * spring - LAND_DAMPING * springVel;
        springVel += acc * dt;
        spring += springVel * dt;
        spring = Math.max(-3f, Math.min(3f, spring));
    }

    public Map<String, BoneAnim> doll() {
        Map<String, BoneAnim> m = new HashMap<>();
        float bob = (float) Math.sin(time * 2.2) * 0.25f;
        float sway = (float) Math.sin(time * 1.3) * 2f;
        float flail = (float) Math.sin(time * 18f) * 10f * down;
        float airborne = Math.max(up, down);

        BoneAnim root = get(m, "2pgdgld");
        root.ty = bob * (1f - airborne) + up * JUMP_RISE_PX + down * FALL_RISE_PX - Math.max(0f, spring) * 0.6f;

        BoneAnim body = get(m, "2pg_body");
        body.rx = down * 10f - up * 4f + spring * 2f;
        body.rz = sway * 0.5f * (1f - airborne);

        BoneAnim head = get(m, "2pg_head");
        head.rx = -up * 8f - down * 12f + spring * 3f;
        head.rz = sway * (1f - airborne);

        float arm = up * JUMP_ARM_DEG + down * FALL_ARM_DEG;
        BoneAnim ra = get(m, "2pg_rightarm");
        ra.rz = -ARM_SIGN * (arm + flail);
        BoneAnim la = get(m, "2pg_leftarm");
        la.rz = ARM_SIGN * (arm - flail);
        return m;
    }

    public Map<String, BoneAnim> cup() {
        Map<String, BoneAnim> m = new HashMap<>();
        BoneAnim c = get(m, "3m4c6l");
        c.ty = -Math.max(0f, spring) * 0.3f;
        c.rx = -down * 2f;
        return m;
    }

    private static BoneAnim get(Map<String, BoneAnim> m, String n) {
        return m.computeIfAbsent(n, k -> new BoneAnim());
    }
}
