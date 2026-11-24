package dev.totemdoll.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TotemDollClient implements ClientModInitializer {
    private static volatile boolean openSettings;

    /** Turns the mod on / off anytime (off = vanilla totem sprite), e.g. doll for survival, vanilla for PvP. */
    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        DollConfig.get().enabled = on;
        DollConfig.save();
        source.sendFeedback(Component.literal("[TotemDoll] " + (on ? "ON (3D totem doll)" : "OFF (vanilla totem)")));
        return 1;
    }

    @Override
    public void onInitializeClient() {
        DollConfig.get();
        DollKeys.register();

        // lets assets/minecraft/items/totem_of_undying.json use {"type":"totemdoll:totem_doll"}
        SpecialModelRenderers.ID_MAPPER.put(
            Identifier.fromNamespaceAndPath("totemdoll", "totem_doll"),
            DollSpecialRenderer.Unbaked.MAP_CODEC);

        // /totemdoll opens the settings screen (opened on the next tick so the chat screen does not close it)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("totemdoll")
                .executes(ctx -> {
                    openSettings = true;
                    return 1;
                })
                .then(ClientCommandManager.literal("toggle")
                    .executes(ctx -> setEnabled(ctx.getSource(), !DollConfig.get().enabled)))
                .then(ClientCommandManager.literal("on")
                    .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off")
                    .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("scale")
                    .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.1f, 4.0f))
                        .executes(ctx -> {
                            float v = FloatArgumentType.getFloat(ctx, "value");
                            DollConfig.get().view("first_r").scale = v;
                            DollConfig.get().view("first_l").scale = v;
                            DollConfig.save();
                            ctx.getSource().sendFeedback(Component.literal("[TotemDoll] First person scale: " + v));
                            return 1;
                        })))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            DollKeys.tick(mc);
            if (openSettings) {
                openSettings = false;
                mc.setScreen(new DollConfigScreen(null));
            }
        });
    }
}
