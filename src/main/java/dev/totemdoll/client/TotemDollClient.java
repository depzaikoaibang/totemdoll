package dev.totemdoll.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

public final class TotemDollClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // lets assets/minecraft/items/totem_of_undying.json use {"type":"totemdoll:totem_doll"}
        SpecialModelRenderers.ID_MAPPER.put(
            Identifier.fromNamespaceAndPath("totemdoll", "totem_doll"),
            DollSpecialRenderer.Unbaked.MAP_CODEC);
    }
}
