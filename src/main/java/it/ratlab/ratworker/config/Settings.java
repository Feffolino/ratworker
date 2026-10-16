package it.ratlab.ratworker.config;

import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.config.Preset.UnlockMode;

/** Resolved server settings: explicit config value first, then the active preset. Server side only. */
public final class Settings {
    private Settings() {}

    public static Preset preset() {
        return ServerConfig.PRESET.get();
    }

    public static boolean enabled() {
        return ServerConfig.ENABLED.get();
    }

    public static int maxWorkersPerPlayer() {
        int v = ServerConfig.MAX_PER_PLAYER.get();
        return v >= 0 ? v : preset().maxWorkersPerPlayer;
    }

    public static int maxWorkersPerChunk() {
        int v = ServerConfig.MAX_PER_CHUNK.get();
        return v >= 0 ? v : preset().maxWorkersPerChunk;
    }

    public static int maxActiveWorkers() {
        int v = ServerConfig.MAX_ACTIVE.get();
        return v >= 0 ? v : preset().maxActiveWorkersPerServer;
    }

    public static boolean hungerEnabled() {
        return ServerConfig.HUNGER_ENABLED.get().or(preset().hunger);
    }

    /** Thirst exists only when Thirst Was Taken is installed; the config can then still turn it off. */
    public static boolean thirstEnabled() {
        return Compat.thirst() && ServerConfig.THIRST_ENABLED.get().or(preset().thirst);
    }

    public static boolean needsKill() {
        return ServerConfig.NEEDS_KILL.get().or(preset().needsKill);
    }

    public static boolean permaDeath() {
        return ServerConfig.PERMA_DEATH.get().or(preset().permaDeath);
    }

    public static boolean requireStageToRecruit() {
        return ServerConfig.REQUIRE_STAGE_TO_RECRUIT.get().or(preset().requireStageToRecruit);
    }

    public static UnlockMode unlockMode() {
        UnlockMode m = ServerConfig.UNLOCK_MODE.get();
        return m == UnlockMode.PRESET ? preset().unlockMode : m;
    }

    /** Value as text for /ratworker config get, showing the preset result for preset-aware keys. */
    public static String describe(String path) {
        var cv = ServerConfig.value(path);
        if (cv == null) return null;
        String raw = String.valueOf(cv.get());
        String resolved = switch (path) {
            case "general.maxWorkersPerPlayer" -> String.valueOf(maxWorkersPerPlayer());
            case "general.maxWorkersPerChunk" -> String.valueOf(maxWorkersPerChunk());
            case "general.maxActiveWorkersPerServer" -> String.valueOf(maxActiveWorkers());
            case "needs.hungerEnabled" -> String.valueOf(hungerEnabled());
            case "needs.thirstEnabled" -> String.valueOf(thirstEnabled());
            case "needs.needsKill" -> String.valueOf(needsKill());
            case "death.permaDeath" -> String.valueOf(permaDeath());
            case "acquisition.requireStageToRecruit" -> String.valueOf(requireStageToRecruit());
            case "unlocking.mode" -> String.valueOf(unlockMode());
            default -> null;
        };
        return resolved == null || resolved.equalsIgnoreCase(raw) ? raw : raw + " (resolved: " + resolved + ")";
    }
}
