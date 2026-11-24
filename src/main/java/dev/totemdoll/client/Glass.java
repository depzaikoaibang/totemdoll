package dev.totemdoll.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * "Liquid glass" look for the mod's screens: light, translucent, frosted panels with a bright top edge,
 * soft gradient, rounded corners and glass buttons / sliders. Nothing here is dark or opaque.
 */
public final class Glass {
    private Glass() { }

    public static int argb(float a, int r, int g, int b) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (r << 16) | (g << 8) | b;
    }

    /** A glass surface. {@code a} = overall strength (0..1), {@code boost} brightens it (hover). */
    public static void surface(GuiGraphics g, int x1, int y1, int x2, int y2, float a, float boost) {
        if (x2 - x1 < 6 || y2 - y1 < 6) return;
        // frosted body: light blue-white gradient (inset by 1 -> rounded corners)
        g.fillGradient(x1 + 1, y1 + 1, x2 - 1, y2 - 1,
            argb(a * 0.26f * boost, 232, 244, 255), argb(a * 0.10f * boost, 168, 204, 255));
        // glossy band along the top
        g.fillGradient(x1 + 2, y1 + 1, x2 - 2, y1 + 1 + Math.max(2, (y2 - y1) / 3),
            argb(a * 0.20f * boost, 255, 255, 255), argb(0f, 255, 255, 255));
        // edges: bright specular top / left, softer bottom / right
        g.fill(x1 + 2, y1, x2 - 2, y1 + 1, argb(a * 0.85f, 255, 255, 255));
        g.fill(x1 + 2, y2 - 1, x2 - 2, y2, argb(a * 0.30f, 255, 255, 255));
        g.fill(x1, y1 + 2, x1 + 1, y2 - 2, argb(a * 0.55f, 255, 255, 255));
        g.fill(x2 - 1, y1 + 2, x2, y2 - 2, argb(a * 0.28f, 255, 255, 255));
        // rounded corner dots
        int c = argb(a * 0.45f, 255, 255, 255);
        g.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, c);
        g.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, c);
        g.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, argb(a * 0.2f, 255, 255, 255));
        g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, argb(a * 0.2f, 255, 255, 255));
    }

    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, float a) {
        surface(g, x1, y1, x2, y2, a, 1f);
    }

    private static int textColor(float a) {
        return (((int) (Math.max(0.25f, Math.min(1f, a)) * 255f)) << 24) | 0xFFFFFF;
    }

    /** Glass button. */
    public static final class Btn extends Button {
        public Btn(int x, int y, int w, int h, Component message, Button.OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            float a = getAlpha();
            boolean hot = isHoveredOrFocused() && this.active;
            surface(g, getX(), getY(), getX() + getWidth(), getY() + getHeight(), a, hot ? 1.8f : 1f);
            int ty = getY() + (getHeight() - 8) / 2;
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, ty, textColor(a));
        }
    }

    /** Glass slider. */
    public static final class Slider extends AbstractSliderButton {
        private final String label;
        private final float min, max;
        private final int decimals;
        private final Consumer<Float> setter;

        public Slider(int x, int y, int w, int h, String label, float min, float max, float value, int decimals,
                      Consumer<Float> setter) {
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

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            float a = getAlpha();
            boolean hot = isHoveredOrFocused() && this.active;
            int x1 = getX(), y1 = getY(), x2 = x1 + getWidth(), y2 = y1 + getHeight();
            surface(g, x1, y1, x2, y2, a, hot ? 1.5f : 0.9f);
            int fill = x1 + 2 + (int) ((getWidth() - 4) * this.value);
            if (fill > x1 + 3) {
                g.fillGradient(x1 + 2, y1 + 2, fill, y2 - 2, argb(a * 0.55f, 150, 205, 255), argb(a * 0.30f, 90, 160, 255));
                g.fill(x1 + 2, y1 + 2, fill, y1 + 3, argb(a * 0.7f, 255, 255, 255));
            }
            int hx = Math.max(x1 + 2, Math.min(x2 - 6, fill - 2));
            g.fill(hx, y1 + 1, hx + 4, y2 - 1, argb(a * 0.95f, 255, 255, 255));
            int ty = y1 + (getHeight() - 8) / 2;
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), (x1 + x2) / 2, ty, textColor(a));
        }
    }
}
