package com.stunslammacro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON config stored at .minecraft/config/stunslammacro.json (repaired on every load). */
public final class StunSlamConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static StunSlamConfig instance = new StunSlamConfig();

    /** Master toggle (J key). Saved so it survives restarts. */
    public boolean enabled = false;
    /**
     * Ticks between steps: 0 = both hits in the same tick,
     * 1 = axe hit on tick 1, mace hit on tick 2 (default), higher = slower.
     */
    public int stepDelayTicks = 1;
    /** Put the hotbar slot back to what it was before the key press. */
    public boolean restoreOriginalSlot = true;

    public static StunSlamConfig get() {
        return instance;
    }

    public static void load() {
        Path path = path();
        StunSlamConfig cfg = new StunSlamConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                StunSlamConfig read = GSON.fromJson(reader, StunSlamConfig.class);
                if (read != null) cfg = read;
            } catch (Exception e) {
                StunSlamClient.LOGGER.warn("Could not read config, using defaults", e);
            }
        }
        cfg.stepDelayTicks = Math.max(0, Math.min(20, cfg.stepDelayTicks));
        instance = cfg;
        cfg.save();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("stunslammacro.json");
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            StunSlamClient.LOGGER.warn("Could not write config", e);
        }
    }
}
