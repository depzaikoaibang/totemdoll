package dev.totemdoll.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Mod settings (Mod Menu's Configure button, key M, or /totemdoll).
 * Right: the settings (title + port credit, porter's Discord, Ancxliy's real socials, then the options).
 * Left: a live "Totem Doll View" of the doll with the current settings. Everything is translucent.
 */
public class DollConfigScreen extends Screen {
    private static final int PANEL_W = 292;
    private static final int MARGIN = 8;
    private static final int LINE = 11;
    private static final float WIDGET_ALPHA = 0.88f;
    private static int keepScroll;

    private final Screen parent;
    private final ItemStack previewStack = new ItemStack(Items.TOTEM_OF_UNDYING);
    private final List<Object[]> labels = new ArrayList<>();   // {String text, Integer baseY, Integer color}
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();
    private int contentTop, contentBottom, shift, scroll, maxScroll, panelX;

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
        int w = PANEL_W - 52;
        panelX = this.width - PANEL_W - MARGIN;                 // settings panel on the RIGHT
        int x = panelX + (PANEL_W - w) / 2;

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
        label("Original pack by Ancxliy. Unofficial 1.21.11 port, personal use.", y, 0x909090);
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
            b -> this.minecraft.setScreen(new DollAdjustScreen(() -> new DollConfigScreen(parent), null)))
            .bounds(x, y, w, 20).build());
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

    /** Translucent: only a light tint, the world / panorama stays visible. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x38000000);
    }

    private static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, 0x88101010);
        g.fill(x1, y1, x2, y1 + 1, 0xAA555555);
        g.fill(x1, y2 - 1, x2, y2, 0xAA555555);
        g.fill(x1, y1, x1 + 1, y2, 0xAA555555);
        g.fill(x2 - 1, y1, x2, y2, 0xAA555555);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int off = shift - scroll;

        // ---- left: live Totem Doll view
        int lx1 = MARGIN, lx2 = panelX - MARGIN, ly1 = MARGIN, ly2 = this.height - MARGIN;
        if (lx2 - lx1 >= 90) {
            panel(g, lx1, ly1, lx2, ly2);
            g.drawCenteredString(this.font, Component.literal("Totem Doll View"), (lx1 + lx2) / 2, ly1 + 8, 0xFFFFFF);
            int size = Math.min(lx2 - lx1 - 16, ly2 - ly1 - 40);
            if (size > 32) {
                float scale = size / 16f;
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate((lx1 + lx2) / 2f, (ly1 + ly2) / 2f + 8f);
                pose.scale(scale, scale);
                g.renderItem(previewStack, -8, -8);
                pose.popMatrix();
            }
            g.drawCenteredString(this.font, Component.literal(DollConfig.MODEL_NAMES[DollConfig.get().modelIndex()]
                + " | " + (DollConfig.get().slim ? "Alex" : "Steve")), (lx1 + lx2) / 2, ly2 - 14, 0xAAAAAA);
        }

        // ---- right: settings
        int x1 = panelX, x2 = panelX + PANEL_W;
        panel(g, x1, contentTop + off, x2, contentBottom + off);
        for (AbstractWidget wd : widgets) wd.setAlpha(WIDGET_ALPHA);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, (x1 + x2) / 2, contentTop + 6 + off, 0xFFFFFF);
        for (Object[] l : labels) {
            g.drawCenteredString(this.font, Component.literal((String) l[0]), (x1 + x2) / 2, (Integer) l[1] + off, (Integer) l[2]);
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
