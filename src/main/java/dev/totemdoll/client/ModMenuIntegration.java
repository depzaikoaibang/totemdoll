package dev.totemdoll.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Gives the mod a "Configure" button in Mod Menu (only loaded when Mod Menu is installed). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DollConfigScreen::new;
    }
}
