package dev.totemdoll.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Key bindings: M opens the settings (like the 26.3 version), a second key (unbound by default) toggles the mod. */
public final class DollKeys {
    private static KeyMapping open, toggle;

    private DollKeys() { }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("totemdoll", "keys"));
        open = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.totemdoll.settings", 77 /* M */, category));
        toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.totemdoll.toggle", -1 /* unbound */, category));
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        while (open.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new DollConfigScreen(null));
        }
        while (toggle.consumeClick()) {
            DollConfig cfg = DollConfig.get();
            cfg.enabled = !cfg.enabled;
            DollConfig.save();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("[TotemDoll] " + (cfg.enabled ? "ON (3D totem doll)" : "OFF (vanilla totem)")), true);
            }
        }
    }
}
