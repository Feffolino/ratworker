package it.ratlab.ratworker.skill;

import it.ratlab.ratworker.config.ServerConfig;

import java.util.Locale;

/**
 * Upgrade branches. HEALTH and STRENGTH belong to the worker; HUNGER, THIRST and SPEED belong to one
 * task (node id "&lt;task id&gt;#&lt;branch&gt;", e.g. "ratworker:crank#speed"). All bought with worker XP.
 */
public enum Branch {
    HEALTH(true), STRENGTH(true), HUNGER(false), THIRST(false), SPEED(false);

    public final boolean global;

    Branch(boolean global) {
        this.global = global;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ServerConfig.BranchSection config() {
        return ServerConfig.BRANCHES.get(this);
    }

    public int maxLevel() {
        return config().maxLevel().get();
    }

    /** Effect of one level: +HP, +damage, -drain fraction, -ticks between clicks. */
    public double perLevel() {
        return config().perLevel().get();
    }

    /** XP to go from {@code level} to {@code level + 1}. */
    public int cost(int level) {
        return (int) Math.round(config().baseCost().get() * Math.pow(config().costGrowth().get(), level));
    }

    public String nodeId(String taskId) {
        return global ? key() : taskId + "#" + key();
    }

    public static Branch fromKey(String key) {
        for (Branch b : values()) if (b.key().equals(key)) return b;
        return null;
    }
}
