package dev.companion.game;

import dev.companion.core.CompanionConfig;
import dev.companion.core.CompanionState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * Client-side entry point. Like a utility client (Meteor), the agent drives the local player:
 * chat lines starting with a trigger are captured instead of being sent to the server.
 */
public final class CompanionMod implements ClientModInitializer {
    public static Agent agent;

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        CompanionConfig cfg = CompanionConfig.load(dir.resolve("companion.json"));
        CompanionState state = CompanionState.load(dir.resolve("companion_state.json"));
        agent = new Agent(cfg, state, dir.resolve("companion_state.json"));

        KeyBinding stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.companion.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.companion"));

        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> !agent.handleChat(msg));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (stopKey.wasPressed()) agent.stopAll("Emergency stop.");
            try {
                agent.tick();
            } catch (RuntimeException e) {
                // a bug in a task must never crash the game: stop and report
                agent.stopAll("§cInternal error, stopped: " + e);
                e.printStackTrace();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(agent::joinNotice));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> agent.stopAll(null));
    }
}
