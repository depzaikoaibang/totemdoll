package dev.totemdoll.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mod settings (Mod Menu's Configure button, key M, or /totemdoll) - laid out like the 26.3 version:
 * the settings on the right, a live rotating 3D "Totem Doll View" on the left, vanilla-style widgets.
 */
public class DollConfigScreen extends Screen {
    private static final int PANEL_W = 292;
    private static final int MARGIN = 8;
    private static final int LINE = 11;
    private static int keepScroll;

    private final Screen parent;
    private final Map<String, PngMask> texCache = new HashMap<>();
    private final DollPreview preview = new DollPreview(this::loadTexture);
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

    /** Reads a PNG through the resource manager (so replaced alex.png / steve.png show up too). */
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

        add(new FloatSlider(x, y, w, 20, "First Person Item Scale", 0.75f, 1.8f, cfg.view("first_r").scale, 2, v -> {
            cfg.view("first_r").scale = v;
            cfg.view("first_l").scale = v;
        }));
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

    /** Vanilla-like dim behind the menu (like the 26.3 screen). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x78000000);
    }

    /** Dark translucent panel with a grey border (like the original menu). */
    static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, float strength) {
        int body = ((int) (0xB0 * strength) << 24) | 0x101010;
        int edge = ((int) (0xFF * strength) << 24) | 0x555555;
        g.fill(x1, y1, x2, y2, body);
        g.fill(x1, y1, x2, y1 + 1, edge);
        g.fill(x1, y2 - 1, x2, y2, edge);
        g.fill(x1, y1, x1 + 1, y2, edge);
        g.fill(x2 - 1, y1, x2, y2, edge);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int off = shift - scroll;

        // ---- left: live 3D Totem Doll View (turntable)
        int lx1 = MARGIN, lx2 = panelX - MARGIN, ly1 = MARGIN, ly2 = this.height - MARGIN;
        if (lx2 - lx1 >= 90) {
            panel(g, lx1, ly1, lx2, ly2, 1f);
            g.drawCenteredString(this.font, Component.literal("Totem Doll View"), (lx1 + lx2) / 2, ly1 + 8, 0xFFFFFFFF);
            int size = Math.min(lx2 - lx1 - 16, ly2 - ly1 - 44);
            if (size > 32) {
                int pixels = Math.max(64, Math.min(560, Math.round(size * (float) this.minecraft.getWindow().getGuiScale())));
                float cell = size / (float) pixels;
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate((lx1 + lx2) / 2f - size / 2f, ly1 + 22f + (ly2 - ly1 - 44 - size) / 2f);
                pose.scale(cell, cell);
                preview.render((x1, y1, x2, y2, c) -> g.fill(x1, y1, x2, y2, c), true, null, pixels);
                pose.popMatrix();
            }
            DollConfig cfg = DollConfig.get();
            g.drawCenteredString(this.font, Component.literal(DollConfig.MODEL_NAMES[cfg.modelIndex()]
                + " | " + (cfg.slim ? "Alex" : "Steve")), (lx1 + lx2) / 2, ly2 - 14, 0xFFAAAAAA);
        }

        // ---- right: settings
        int x1 = panelX, x2 = panelX + PANEL_W;
        panel(g, x1, contentTop + off, x2, contentBottom + off, 1f);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, (x1 + x2) / 2, contentTop + 6 + off, 0xFFFFFFFF);
        for (Object[] l : labels) {
            g.drawCenteredString(this.font, Component.literal((String) l[0]), (x1 + x2) / 2, (Integer) l[1] + off,
                0xFF000000 | (Integer) l[2]);
        }
    }

    @Override
    public void onClose() {
        DollConfig.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ slider helper (vanilla look)
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
