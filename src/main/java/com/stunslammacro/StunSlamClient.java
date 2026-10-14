package com.stunslammacro;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Two keybinds (both rebindable in Controls):
 *  - toggle key (J): master ON/OFF switch, saved to the config file
 *  - sequence key (R): runs one stun slam, only while the toggle is ON
 */
public class StunSlamClient implements ClientModInitializer {
    public static final String MOD_ID = "stunslammacro";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final StunSlamSequence SEQUENCE = new StunSlamSequence();
    private static KeyBinding toggleKey;
    private static KeyBinding sequenceKey;

    @Override
    public void onInitializeClient() {
        StunSlamConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stunslammacro.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        sequenceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stunslammacro.activate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftClient client) {
        StunSlamConfig cfg = StunSlamConfig.get();

        // Toggle: each press flips the state once.
        while (toggleKey.wasPressed()) {
            if (client.currentScreen != null || client.player == null) continue;
            cfg.enabled = !cfg.enabled;
            cfg.save();
            client.player.sendMessage(
                    Text.literal(cfg.enabled ? "StunSlam: ON" : "StunSlam: OFF")
                            .formatted(cfg.enabled ? Formatting.GREEN : Formatting.RED),
                    true); // true = action bar
        }

        // Sequence key: consume every queued press so one press can never start two runs.
        boolean pressed = false;
        while (sequenceKey.wasPressed()) {
            pressed = true;
        }
        if (pressed && cfg.enabled && !SEQUENCE.isRunning() && client.currentScreen == null) {
            SEQUENCE.start(client);
        }
        SEQUENCE.tick(client);
    }
}
