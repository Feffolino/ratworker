package it.ratlab.ratworker.task.builtin;

import it.ratlab.ratworker.api.RegisterWorkerTasksEvent;
import it.ratlab.ratworker.api.TaskNumbers;
import net.minecraftforge.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in tasks. DEFAULTS is plain data (no registry access) because the server config spec reads it at
 * class-init time to create one [tasks.&lt;id&gt;] section per built-in task, including tasks of later phases.
 */
public final class BuiltinTasks {
    public static final Map<String, TaskNumbers> DEFAULTS = new LinkedHashMap<>();

    static {
        // range = distance at which following starts
        DEFAULTS.put("follow", TaskNumbers.of(1.0, 1.0, 1.0, 5, 1).with(TaskNumbers.LEARN_XP, 0)
                .with("stopDistance", 2.5).with("teleportDistance", 24));
        // range = how far the worker may drift before walking back; drains at the idle rate
        DEFAULTS.put("stay", TaskNumbers.of(1.0, 1.0, 1.0, 1.5, 1).with(TaskNumbers.LEARN_XP, 0).with(TaskNumbers.XP_RATE, 0.5));
        // phase 2-5 tasks: sections exist now so packs can tune them before they ship
        // range = search radius for a crank (the worker walks there); reverse = 1 turns it the other way (sneak + use);
        // holdInterval = ticks between clicks before speed upgrades (one click spins a crank 10 ticks, so 16 leaves gaps)
        DEFAULTS.put("crank", TaskNumbers.of(1.5, 1.5, 1.0, 16, 1).with(TaskNumbers.LEARN_XP, 30).with(TaskNumbers.XP_RATE, 1.5)
                .with("reverse", 0).with("holdInterval", 16).with("minHoldInterval", 4));
        // range = radius of the field around the start block; actionCooldown = ticks between farm actions;
        // till = 1 tills dirt near water; depositAt = produce count that sends it to the supply
        DEFAULTS.put("farm", TaskNumbers.of(1.0, 1.2, 1.0, 6, 1).with(TaskNumbers.LEARN_XP, 40).with(TaskNumbers.XP_RATE, 1.5)
                .with("actionCooldown", 20).with("till", 1).with("depositAt", 16));
        DEFAULTS.put("guard", TaskNumbers.of(1.2, 1.2, 1.0, 12, 1).with(TaskNumbers.LEARN_XP, 60));
        DEFAULTS.put("haul", TaskNumbers.of(1.0, 1.0, 1.0, 16, 1).with(TaskNumbers.LEARN_XP, 40));
    }

    private BuiltinTasks() {}

    public static void register(RegisterWorkerTasksEvent event) {
        event.register(new FollowTask());
        event.register(new StayTask());
        event.register(new FarmTask());
        if (ModList.get().isLoaded("create")) event.register(new CrankTask());
    }
}
