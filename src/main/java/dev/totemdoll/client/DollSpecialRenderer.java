package dev.totemdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Renders the Totem of Undying as a doll sitting in a cup, with procedural jump/fall animation.
 *
 * Pipeline (1.21.9+): extractArgument -> submit(SubmitNodeCollector) -> custom geometry flushed later in the frame.
 * Display transforms (hand / gui / ground) come from assets/totemdoll/models/item/totem_doll.json.
 */
public final class DollSpecialRenderer implements SpecialModelRenderer<Boolean> {
    private static final Identifier SKIN = Identifier.fromNamespaceAndPath("totemdoll", "textures/entity/skin.png");
    private static final Identifier CUP = Identifier.fromNamespaceAndPath("totemdoll", "textures/entity/bucket.png");

    private final DollModel doll = DollModel.load("/assets/totemdoll/bedrock/models/steve.geo.json");
    private final DollModel cup = DollModel.load("/assets/totemdoll/bedrock/models/bucket.geo.json");
    private final DollAnim anim = new DollAnim();
    private long lastNanos = System.nanoTime();

    @Override
    public @Nullable Boolean extractArgument(ItemStack stack) {
        return Boolean.TRUE;
    }

    @Override
    public void submit(@Nullable Boolean arg, ItemDisplayContext ctx, PoseStack ps, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outlineColor) {
        stepAnimation();
        ps.pushPose();
        ps.translate(0.5f, 0.0f, 0.5f);
        // model px -> blocks. Negative Z = Bedrock x-mirror + 180deg turn, so the doll faces the viewer.
        final float s = 1f / 16f;
        ps.scale(s, s, -s);

        collector.submitCustomGeometry(ps, RenderType.entityCutoutNoCull(CUP), (pose, vc) ->
            cup.render(anim.cup(), (x, y, z, u, v, nx, ny, nz) ->
                vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                  .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz)));

        collector.submitCustomGeometry(ps, RenderType.entityCutoutNoCull(SKIN), (pose, vc) ->
            doll.render(anim.doll(), (x, y, z, u, v, nx, ny, nz) ->
                vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                  .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz)));
        ps.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new org.joml.Vector3f(0.2f, 0f, 0.2f));
        output.accept(new org.joml.Vector3f(0.8f, 1f, 0.8f));
    }

    private void stepAnimation() {
        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        if (dt < 0.0005f) return;               // submit() can run several times per frame
        lastNanos = now;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) anim.update(dt, true, 0);
        else anim.update(dt, p.onGround(), p.getDeltaMovement().y);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new DollSpecialRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
