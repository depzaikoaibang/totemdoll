package dev.totemdoll.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Mod settings (opened from Mod Menu's Configure button or with /totemdoll).
 * Header: title + port credit, Discord of the porter, then the real socials of the original author.
 * The whole content scrolls with the mouse wheel when the window is too small.
 */
public class DollConfigScreen extends Screen {
    private static final int PANEL_W = 276;
    private static final int LINE = 11;
    private static int keepScroll;

    private final Screen parent;
    private final List<Object[]> labels = new ArrayList<>();   // {String text, Integer baseY, Integer color}
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();
    private int contentTop, contentBottom, shift, scroll, maxScroll;

    public DollConfigScreen(Screen parent) {
        super(Component.literal("3D My Totem Doll | By Ancxliy | port by We1rd\u30C4"));
        this.parent = parent;
        this.scroll = keepScroll;
        keepScroll = 0;
    }

    private <T extends AbstractWidget> T add(T w) {
        addRenderableWidget(w);
        widgets.add(w);
        widgetBaseY.add(w.getY());
        return w;
    }

    private void label(String text, int y, int color) {
        labels.add(new Object[]{text, y, color});
    }

    @Override
    protected void init() {
        labels.clear();
        widgets.clear();
        widgetBaseY.clear();
        DollConfig cfg = DollConfig.get();
        int w = 226, x = this.width / 2 - w / 2;

        int y = 8;
        contentTop = y;
        y += 6;
        // ---- header: the title is drawn in render(); porter contact first, then Ancxliy's real socials
        label("Discord: itz_sh4rk", y + LINE, 0x8EA1FF);
        y += LINE * 2 + 4;
        label("Ancxliy Social Media", y, 0xFFFF55);
        y += LINE;
        label("Tiktok: [ @ancxliy ]", y, 0xDDDDDD);
        y += LINE;
        label("YouTube: [ @ancraff ]", y, 0xDDDDDD);
        y += LINE;
        label("Other Links: [ https://linktr.ee/ancxliy ]", y, 0x55FFFF);
        y += LINE + 3;
        label("Original pack by Ancxliy. Unofficial 1.21.11 port, personal use.", y, 0x808080);
        y += LINE + 8;

        // ---- settings
        add(Button.builder(toggleText("Enable Mod", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(toggleText("Enable Mod", cfg.enabled));
        }).bounds(x, y, w, 20).build());
        y += 24;

        add(Button.builder(toggleText("3D Totem", cfg.totem3d), b -> {
            cfg.totem3d = !cfg.totem3d;
            b.setMessage(toggleText("3D Totem", cfg.totem3d));
        }).bounds(x, y, w, 20).build());
        y += 26;

        label("Model Selection | By Ancxliy", y, 0xFFFFFF);
        y += 11;
        add(Button.builder(Component.literal(DollConfig.MODEL_NAMES[cfg.modelIndex()]), b -> {
            int i = (cfg.modelIndex() + 1) % DollConfig.MODELS.length;
            cfg.model = DollConfig.MODELS[i];
            keepScroll = scroll;
            DollConfig.save();
            this.minecraft.setScreen(new DollConfigScreen(parent));     // reload per-model values
        }).bounds(x, y, w, 20).build());
        y += 26;

        label("Textures Player Arm Totem Model", y, 0xFFFFFF);
        y += 11;
        add(Button.builder(Component.literal(armsText(cfg)), b -> {
            cfg.slim = !cfg.slim;
            b.setMessage(Component.literal(armsText(cfg)));
        }).bounds(x, y, w, 20).build());
        y += 26;

        add(Button.builder(toggleText("3D Skin Layer", cfg.layer3d), b -> {
            cfg.layer3d = !cfg.layer3d;
            b.setMessage(toggleText("3D Skin Layer", cfg.layer3d));
        }).bounds(x, y, w, 20).build());
        y += 24;

        add(Button.builder(toggleText("Blink", cfg.blink), b -> {
            cfg.blink = !cfg.blink;
            b.setMessage(toggleText("Blink", cfg.blink));
        }).bounds(x, y, w, 20).build());
        y += 24;

        add(Button.builder(toggleText("Animations", cfg.animations), b -> {
            cfg.animations = !cfg.animations;
            b.setMessage(toggleText("Animations", cfg.animations));
        }).bounds(x, y, w, 20).build());
        y += 24;

        DollConfig.View first = cfg.view("first");
        add(new FloatSlider(x, y, w, 20, "First Person Item Scale", 0.75f, 1.8f, first.scale, 2, v -> first.scale = v));
        y += 28;

        label("Skins for 3D Totem Player", y, 0xFFFF55);
        y += 11;
        label("To change the skin: download it from namemc.com,", y, 0xAAAAAA);
        y += LINE;
        label("rename it to alex.png (slim) or steve.png (normal)", y, 0xAAAAAA);
        y += LINE;
        label("and replace that file in the mod / resource pack.", y, 0xAAAAAA);
        y += LINE + 8;

        add(Button.builder(Component.literal("Adjust Doll Position..."),
            b -> this.minecraft.setScreen(new DollAdjustScreen(this, null))).bounds(x, y, w, 20).build());
        y += 26;

        add(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y, w, 20).build());
        contentBottom = y + 26;

        int total = contentBottom - contentTop;
        shift = Math.max(0, (this.height - total) / 2 - contentTop);
        maxScroll = Math.max(0, contentBottom + 8 - this.height);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        applyScroll();
    }

    private void applyScroll() {
        for (int i = 0; i < widgets.size(); i++) widgets.get(i).setY(widgetBaseY.get(i) + shift - scroll);
    }

    private static String armsText(DollConfig c) { return c.slim ? "Slim [ Alex ]" : "Wide [ Steve ]"; }

    private static Component toggleText(String name, boolean on) {
        return Component.literal(name + ": ")
            .append(Component.literal(on ? "\u2714" : "\u2718").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY) * 16));
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int off = shift - scroll;
        int x1 = this.width / 2 - PANEL_W / 2, x2 = x1 + PANEL_W;
        int top = contentTop + off, bottom = contentBottom + off;
        g.fill(x1, top, x2, bottom, 0xC0101010);
        g.fill(x1, top, x2, top + 1, 0xFF555555);
        g.fill(x1, bottom - 1, x2, bottom, 0xFF555555);
        g.fill(x1, top, x1 + 1, bottom, 0xFF555555);
        g.fill(x2 - 1, top, x2, bottom, 0xFF555555);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, contentTop + 6 + off, 0xFFFFFF);
        for (Object[] l : labels) {
            g.drawCenteredString(this.font, Component.literal((String) l[0]), this.width / 2, (Integer) l[1] + off, (Integer) l[2]);
        }
    }

    @Override
    public void onClose() {
        DollConfig.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ slider helper
    static final class FloatSlider extends AbstractSliderButton {
        private final String label;
        private final float min, max;
        private final int decimals;
        private final Consumer<Float> setter;

        FloatSlider(int x, int y, int w, int h, String label, float min, float max, float value, int decimals, Consumer<Float> setter) {
            super(x, y, w, h, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.setter = setter;
            updateMessage();
        }

        private float current() {
            float v = min + (float) this.value * (max - min);
            float p = (float) Math.pow(10, decimals);
            return Math.round(v * p) / p;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, "%." + decimals + "f", current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
