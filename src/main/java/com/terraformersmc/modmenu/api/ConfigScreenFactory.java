package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/**
 * COMPILE-TIME STUB of Mod Menu's API (excluded from the built jar, see build.gradle).
 * At runtime the real Mod Menu provides this interface, so there is no hard dependency on it.
 */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
