package dev.totemdoll.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * "Adjust Doll Position" (liquid glass): rotation / offset / scale for each view of the CURRENT model.
 * First / third person can be edited for Both hands, only the Left hand or only the Right hand.
 * Right side: a live preview with the settings applied. The glass panel is translucent and clearer
 * while the mouse is over it, so the doll in your hand stays visible. Hold a Totem to see it in the world.
 */
public class DollAdjustScreen extends Screen {
    private enum F { RX, RY, RZ, OX, OY, OZ, SC }

    private static int viewIndex = 0;
    private static int handMode = 0;               // 0 = both, 1 = left, 2 = right
    private static final String[] HAND_NAMES = {"Both", "Left", "Right"};
    private static final float FADED = 0.45f;

    private final Supplier<Screen> back;
    private CameraType restoreCamera;
    private final Map<String, PngMask> texCache = new HashMap<>();
    private final DollPreview preview = new DollPreview(this::loadTexture);
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int px, py, pw, ph;
    private float fade = 1f;

    /** @param back creates the screen to return to (a fresh settings screen, so its values are up to date) */
    public DollAdjustScreen(Supplier<Screen> back, CameraType restoreCamera) {
        super(Component.literal("Adjust Doll Position"));
        this.back = back;
        this.restoreCamera = restoreCamera;
    }

    PngMask loadTexture(String id) {
        if (texCache.containsKey(id)) return texCache.get(id);
        PngMask m = null;
        try {
            int c = id.indexOf(':');
            Identifier ident = Identifier.fromNamespaceAndPath(id.substring(0, c), id.substring(c + 1));
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(ident);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    m = PngMask.load(in);
                }
            }
        } catch (Exception ignored) {
            m = null;
        }
        texCache.put(id, m);
        return m;
    }

    private <T extends AbstractWidget> T track(T w) {
        addRenderableWidget(w);
        widgets.add(w);
        return w;
    }

    // ------------------------------------------------------------------ settings access (both / left / right)
    private static float sign(F f) { return (f == F.RY || f == F.RZ || f == F.OX) ? -1f : 1f; }

    private static float get(DollConfig.View v, F f) {
        switch (f) {
            case RX: return v.rx;
            case RY: return v.ry;
            case RZ: return v.rz;
            case OX: return v.ox;
            case OY: return v.oy;
            case OZ: return v.oz;
            default: return v.scale;
        }
    }

    private static void put(DollConfig.View v, F f, float val) {
        switch (f) {
            case RX: v.rx = val; break;
            case RY: v.ry = val; break;
            case RZ: v.rz = val; break;
            case OX: v.ox = val; break;
            case OY: v.oy = val; break;
            case OZ: v.oz = val; break;
            default: v.scale = val; break;
        }
    }

    /** The view shown / used as the source: right hand (or left when editing the left hand only). */
    private static DollConfig.View source(String view) {
        DollConfig cfg = DollConfig.get();
        if (!DollConfig.hasHands(view)) return cfg.view(view);
        return cfg.view(view + (handMode == 1 ? "_l" : "_r"));
    }

    private static void set(String view, F f, float val) {
        DollConfig cfg = DollConfig.get();
        if (!DollConfig.hasHands(view)) { put(cfg.view(view), f, val); return; }
        DollConfig.View r = cfg.view(view + "_r"), l = cfg.view(view + "_l");
        if (handMode == 0) { put(r, f, val); put(l, f, val * sign(f)); }        // left hand = mirror of the right
        else if (handMode == 1) put(l, f, val);
        else put(r, f, val);
    }

    private static void reset(String view) {
        DollConfig cfg = DollConfig.get();
        for (F f : F.values()) {
            float d = f == F.SC ? 1f : 0f;
            if (!DollConfig.hasHands(view)) { put(cfg.view(view), f, d); continue; }
            if (handMode != 1) put(cfg.view(view + "_r"), f, d);
            if (handMode != 2) put(cfg.view(view + "_l"), f, d);
        }
    }

    // ------------------------------------------------------------------ screen
    @Override
    protected void init() {
        widgets.clear();
        if (restoreCamera == null) restoreCamera = this.minecraft.options.getCameraType();
        String key = DollConfig.VIEWS[viewIndex];
        // show the right camera for the view being edited, immediately
        if (key.equals("third")) this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        else if (key.equals("first")) this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);

        DollConfig.View src = source(key);
        int colW = 140, gap = 6, x = 12, y = 40, step = 22;
        int x2 = x + colW + gap, full = colW * 2 + gap;
        px = x - 6;
        py = 6;
        pw = full + 12;

        track(new Glass.Btn(x, y, full, 20, Component.literal("View: " + DollConfig.VIEW_NAMES[viewIndex]), b -> {
            DollConfig.save();
            viewIndex = (viewIndex + 1) % DollConfig.VIEWS.length;
            this.minecraft.setScreen(new DollAdjustScreen(back, restoreCamera));
        }));
        y += step;

        if (DollConfig.hasHands(key)) {
            track(new Glass.Btn(x, y, full, 20, Component.literal("Edit hand: " + HAND_NAMES[handMode]), b -> {
                DollConfig.save();
                handMode = (handMode + 1) % 3;
                this.minecraft.setScreen(new DollAdjustScreen(back, restoreCamera));
            }));
            y += step;
        }

        track(new Glass.Slider(x, y, colW, 20, "Rotation X", -180f, 180f, get(src, F.RX), 0, f -> set(key, F.RX, f)));
        track(new Glass.Slider(x2, y, colW, 20, "Offset X", -16f, 16f, get(src, F.OX), 1, f -> set(key, F.OX, f)));
        y += step;
        track(new Glass.Slider(x, y, colW, 20, "Rotation Y", -180f, 180f, get(src, F.RY), 0, f -> set(key, F.RY, f)));
        track(new Glass.Slider(x2, y, colW, 20, "Offset Y", -16f, 16f, get(src, F.OY), 1, f -> set(key, F.OY, f)));
        y += step;
        track(new Glass.Slider(x, y, colW, 20, "Rotation Z", -180f, 180f, get(src, F.RZ), 0, f -> set(key, F.RZ, f)));
        track(new Glass.Slider(x2, y, colW, 20, "Offset Z", -16f, 16f, get(src, F.OZ), 1, f -> set(key, F.OZ, f)));
        y += step;
        track(new Glass.Slider(x, y, full, 20, "Scale", 0.1f, 3f, get(src, F.SC), 2, f -> set(key, F.SC, f)));
        y += step;

        track(new Glass.Btn(x, y, full, 20, Component.literal("Reset To Original Values"), b -> {
            reset(key);
            DollConfig.save();
            this.minecraft.setScreen(new DollAdjustScreen(back, restoreCamera));
        }));
        y += step;

        track(new Glass.Btn(x, y, full, 20, Component.literal("Done"), b -> onClose()));
        ph = y + 26 - py;
    }

    /** No dark / blurred background: the doll must stay visible. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean over = mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph;
        fade += ((over ? 1f : FADED) - fade) * 0.25f;
        for (AbstractWidget w : widgets) w.setAlpha(fade);

        Glass.panel(g, px, py, px + pw, py + ph, fade);
        super.render(g, mouseX, mouseY, partialTick);
        int tc = (((int) (Math.max(0.35f, fade) * 255f)) << 24) | 0xFFFFFF;
        g.drawString(this.font, this.title, px + 6, py + 6, tc);
        String hint = DollConfig.hasHands(DollConfig.VIEWS[viewIndex])
            ? "hold a Totem of Undying (left / right hand) to see it live" : "live preview on the right";
        g.drawString(this.font, Component.literal(DollConfig.MODEL_NAMES[DollConfig.get().modelIndex()] + "  |  " + hint),
            px + 6, py + 20, tc);

        // ---- live preview with the settings applied (right side)
        int size = Math.min(150, this.height - 60);
        int rx2 = this.width - 12, rx1 = rx2 - size - 16, ry1 = 6, ry2 = ry1 + size + 34;
        Glass.panel(g, rx1, ry1, rx2, ry2, Math.max(0.6f, fade));
        g.drawCenteredString(this.font, Component.literal("Preview"), (rx1 + rx2) / 2, ry1 + 6, 0xFFFFFFFF);
        float cell = size / (float) DollPreview.RES;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(rx1 + 8f, ry1 + 22f);
        pose.scale(cell, cell);
        preview.render((x1, y1, x2, y2, c) -> g.fill(x1, y1, x2, y2, c), false, source(DollConfig.VIEWS[viewIndex]));
        pose.popMatrix();
    }

    @Override
    public void onClose() {
        DollConfig.save();
        if (restoreCamera != null) this.minecraft.options.setCameraType(restoreCamera);
        this.minecraft.setScreen(back.get());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
