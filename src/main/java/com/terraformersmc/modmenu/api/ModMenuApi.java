package com.terraformersmc.modmenu.api;

/** COMPILE-TIME STUB of Mod Menu's API (excluded from the built jar, see build.gradle). */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }
}
