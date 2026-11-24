package dev.totemdoll.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * "Adjust Doll Position": rotation / offset / scale for each view of the CURRENT model.
 * The world is not darkened and the panel is translucent (clearer while the mouse is over it), so the
 * doll in your hand stays visible. Hold a Totem of Undying to see the changes live.
 */
public class DollAdjustScreen extends Screen {
    private static int viewIndex = 0;
    private static final float FADED = 0.40f;

    private final Supplier<Screen> back;
    private CameraType restoreCamera;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int px, py, pw, ph;
    private float fade = 1f;

    /** @param back creates the screen to return to (a fresh settings screen, so its values are up to date) */
    public DollAdjustScreen(Supplier<Screen> back, CameraType restoreCamera) {
        super(Component.literal("Adjust Doll Position"));
        this.back = back;
        this.restoreCamera = restoreCamera;
    }

    private <T extends AbstractWidget> T track(T w) {
        addRenderableWidget(w);
        widgets.add(w);
        return w;
    }

    @Override
    protected void init() {
        widgets.clear();
        if (restoreCamera == null) restoreCamera = this.minecraft.options.getCameraType();
        String key = DollConfig.VIEWS[viewIndex];
        // show the right camera for the view being edited
        if (key.equals("third")) this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        else if (key.equals("first")) this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);

        DollConfig cfg = DollConfig.get();
        DollConfig.View v = cfg.view(key);
        int colW = 140, gap = 6, x = 12, y = 40, step = 22;
        int x2 = x + colW + gap, full = colW * 2 + gap;
        px = x - 6;
        py = 6;
        pw = full + 12;

        track(Button.builder(Component.literal("View: " + DollConfig.VIEW_NAMES[viewIndex]), b -> {
            DollConfig.save();
            viewIndex = (viewIndex + 1) % DollConfig.VIEWS.length;
            this.minecraft.setScreen(new DollAdjustScreen(back, restoreCamera));
        }).bounds(x, y, full, 20).build());
        y += step;

        track(new DollConfigScreen.FloatSlider(x, y, colW, 20, "Rotation X", -180f, 180f, v.rx, 0, f -> v.rx = f));
        track(new DollConfigScreen.FloatSlider(x2, y, colW, 20, "Offset X", -16f, 16f, v.ox, 1, f -> v.ox = f));
        y += step;
        track(new DollConfigScreen.FloatSlider(x, y, colW, 20, "Rotation Y", -180f, 180f, v.ry, 0, f -> v.ry = f));
        track(new DollConfigScreen.FloatSlider(x2, y, colW, 20, "Offset Y", -16f, 16f, v.oy, 1, f -> v.oy = f));
        y += step;
        track(new DollConfigScreen.FloatSlider(x, y, colW, 20, "Rotation Z", -180f, 180f, v.rz, 0, f -> v.rz = f));
        track(new DollConfigScreen.FloatSlider(x2, y, colW, 20, "Offset Z", -16f, 16f, v.oz, 1, f -> v.oz = f));
        y += step;
        track(new DollConfigScreen.FloatSlider(x, y, full, 20, "Scale", 0.1f, 3f, v.scale, 2, f -> v.scale = f));
        y += step;

        track(Button.builder(Component.literal("Reset To Original Values"), b -> {
            v.rx = v.ry = v.rz = v.ox = v.oy = v.oz = 0f;
            v.scale = 1f;
            DollConfig.save();
            this.minecraft.setScreen(new DollAdjustScreen(back, restoreCamera));
        }).bounds(x, y, full, 20).build());
        y += step;

        track(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y, full, 20).build());
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

        int bg = ((int) (fade * 0.65f * 255f) << 24) | 0x101010;
        g.fill(px, py, px + pw, py + ph, bg);
        super.render(g, mouseX, mouseY, partialTick);
        int tc = ((int) (Math.max(0.35f, fade) * 255f) << 24) | 0xFFFFFF;
        g.drawString(this.font, this.title, px + 6, py + 6, tc);
        g.drawString(this.font, Component.literal(DollConfig.MODEL_NAMES[DollConfig.get().modelIndex()]
            + "  |  hold a Totem of Undying to see changes"), px + 6, py + 20, tc);
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
