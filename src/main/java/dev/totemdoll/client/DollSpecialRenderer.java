package dev.totemdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Renders the Totem of Undying as a doll (Alex = slim or Steve = normal skin) sitting in a bucket / flower bucket / coffee / matcha cup,
 * with the pack's keyframe animations and an optional 3D skin layer.
 * Model, animations, 3D layer and per-view rotation/offset/scale come from {@link DollConfig}.
 */
public final class DollSpecialRenderer implements SpecialModelRenderer<Boolean> {
    private static final Identifier ALEX = Identifier.fromNamespaceAndPath("totemdoll", "textures/entity/alex.png");
    private static final Identifier STEVE = Identifier.fromNamespaceAndPath("totemdoll", "textures/entity/steve.png");

    private final Map<String, DollModel> models = new HashMap<>();
    private final Map<String, Identifier> cupTextures = new HashMap<>();
    private final DollAnimator anim = new DollAnimator();
    private long lastNanos = System.nanoTime();

    @Override
    public @Nullable Boolean extractArgument(ItemStack stack) {
        return Boolean.TRUE;
    }

    @Override
    public void submit(@Nullable Boolean arg, ItemDisplayContext ctx, PoseStack ps, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outlineColor) {
        DollConfig cfg = DollConfig.get();
        anim.setStill(!cfg.animations);
        stepAnimation();
        DollConfig.View v = cfg.view(viewKey(ctx));

        ps.pushPose();
        // user adjustment around the centre of the item cube
        ps.translate(0.5f, 0.5f, 0.5f);
        ps.translate(v.ox / 16f, v.oy / 16f, v.oz / 16f);
        ps.mulPose(new Quaternionf().rotationZYX(
            (float) Math.toRadians(v.rz), (float) Math.toRadians(v.ry), (float) Math.toRadians(v.rx)));
        ps.scale(v.scale, v.scale, v.scale);
        ps.translate(-0.5f, -0.5f, -0.5f);

        ps.translate(0.5f, 0.0f, 0.5f);
        // model px -> blocks, turned 180 degrees around Y so the doll faces the viewer (x and z negated)
        final float s = 1f / 16f;
        ps.scale(-s, s, -s);

        Map<String, DollModel.BoneAnim> pose = anim.pose();
        Identifier cupTex = cupTextures.computeIfAbsent(cfg.model,
            n -> Identifier.fromNamespaceAndPath("totemdoll", "textures/entity/" + n + ".png"));
        draw(collector, ps, model(cfg.model, null), pose, false, cupTex, light, overlay);
        Identifier skin = cfg.slim ? ALEX : STEVE;
        draw(collector, ps, model(cfg.slim ? "alex" : "steve", skin), pose, cfg.layer3d, skin, light, overlay);
        ps.popPose();
    }

    private static void draw(SubmitNodeCollector collector, PoseStack ps, DollModel model,
                             Map<String, DollModel.BoneAnim> pose, boolean layer3d, Identifier tex,
                             int light, int overlay) {
        collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(tex), (p, vc) ->
            model.render(pose, layer3d, (x, y, z, u, w, nx, ny, nz) ->
                vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, w)
                  .setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz)));
    }

    /** @param skinTexture texture whose opacity drives the 3D skin layer (read through the resource manager,
     *                     so a replaced alex.png / steve.png in a resource pack is honoured), or null */
    private DollModel model(String name, @Nullable Identifier skinTexture) {
        return models.computeIfAbsent(name, n -> {
            DollModel m = DollModel.load("/assets/totemdoll/bedrock/models/" + n + ".geo.json");
            if (skinTexture != null) m.setMask(loadMask(skinTexture));
            return m;
        });
    }

    private static @Nullable PngMask loadMask(Identifier texture) {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    return PngMask.load(in);
                }
            }
        } catch (Exception ignored) {
            // unsupported PNG (e.g. 16-bit): the doll falls back to the plain inflated outer layer
        }
        return null;
    }

    private static String viewKey(ItemDisplayContext c) {
        switch (c) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                return "first";
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                return "third";
            case GUI:
                return "gui";
            case FIXED:
                return "fixed";
            default:
                return "ground";
        }
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
        if (p == null) {
            anim.update(dt, true, 0, false);
        } else {
            boolean holding = p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
            anim.update(dt, p.onGround(), p.getDeltaMovement().y, holding);
        }
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
