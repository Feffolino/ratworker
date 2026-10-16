package it.ratlab.ratworker.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tunable numbers of a task. Resolution order (later wins):
 * the task's own defaults, then datapack {@code data/<ns>/ratworker/task/<id>.json}, then the server config
 * section {@code tasks.<id>} when a key is set to a value >= 0.
 */
public final class TaskNumbers {
    public static final String HUNGER_COST = "hungerCost";
    public static final String THIRST_COST = "thirstCost";
    public static final String SPEED = "speedMultiplier";
    public static final String RANGE = "range";
    public static final String LEVELS = "levels";
    /** Worker XP needed (with a tome) to learn the task. */
    public static final String LEARN_XP = "learnXp";
    /** Multiplier of skills.xpPerMinute while the task runs. */
    public static final String XP_RATE = "xpRate";

    private final Map<String, Double> values = new LinkedHashMap<>();

    public static TaskNumbers of(double hungerCost, double thirstCost, double speed, double range, int levels) {
        TaskNumbers n = new TaskNumbers();
        n.values.put(HUNGER_COST, hungerCost);
        n.values.put(THIRST_COST, thirstCost);
        n.values.put(SPEED, speed);
        n.values.put(RANGE, range);
        n.values.put(LEVELS, (double) levels);
        n.values.put(LEARN_XP, 30.0);
        n.values.put(XP_RATE, 1.0);
        return n;
    }

    /** Adds a task-specific key (e.g. "teleportDistance"). It gets its own config entry for built-in tasks. */
    public TaskNumbers with(String key, double value) {
        values.put(key, value);
        return this;
    }

    public double get(String key, double fallback) {
        Double v = values.get(key);
        return v == null ? fallback : v;
    }

    public Map<String, Double> asMap() {
        return values;
    }
}
