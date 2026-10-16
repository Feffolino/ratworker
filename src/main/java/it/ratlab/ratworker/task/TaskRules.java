package it.ratlab.ratworker.task;

import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.config.Preset.UnlockMode;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.data.WorkerDataLoader;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.world.WorkerSavedData;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Unlocking, enabling and number resolution for tasks. Server side. */
public final class TaskRules {
    private TaskRules() {}

    private static ServerConfig.TaskSection section(WorkerTask task) {
        return "ratworker".equals(task.id().getNamespace()) ? ServerConfig.TASKS.get(task.id().getPath()) : null;
    }

    public static boolean enabled(WorkerTask task) {
        ServerConfig.TaskSection s = section(task);
        return s == null || s.enabled().get();
    }

    /** Stage name of the task: config unlockStage, then the task's own key, then the template. */
    public static String stage(WorkerTask task) {
        ServerConfig.TaskSection s = section(task);
        if (s != null && !s.unlockStage().get().isBlank()) return s.unlockStage().get();
        if (task.stageKey() != null) return task.stageKey();
        return ServerConfig.STAGE_TEMPLATE.get().replace("<task>", task.id().getPath());
    }

    public static String levelStage(WorkerTask task, int level) {
        return ServerConfig.LEVEL_STAGE_TEMPLATE.get().replace("<task>", task.id().getPath()).replace("<level>", String.valueOf(level));
    }

    /** True if the player has the stage through KubeJS, a scoreboard tag or /ratworker unlock. */
    public static boolean hasStage(ServerPlayer player, String stage) {
        if (WorkerSavedData.get(player.server).hasUnlock(player.getUUID(), stage)) return true;
        if (ServerConfig.SCOREBOARD_TAGS.get() && player.getTags().contains(stage)) return true;
        return Compat.hasKubeJSStage(player, stage);
    }

    public static boolean isOpBypass(ServerPlayer player) {
        return ServerConfig.OP_BYPASS.get() && player.hasPermissions(2);
    }

    /** Locked tasks are hidden from the command screen, not greyed out. */
    public static boolean isUnlocked(ServerPlayer player, WorkerEntity worker, WorkerTask task) {
        if (!enabled(task) || !task.isUnlocked(player, worker)) return false;
        UnlockMode mode = Settings.unlockMode();
        if (mode == UnlockMode.FREE) return true;
        // tomes train the worker, so an op owner sees the same tasks as anyone (use /ratworker skills to cheat)
        if (mode == UnlockMode.TOMES) return worker.skills().knows(task);
        if (isOpBypass(player)) return true;
        String stage = stage(task);
        return switch (mode) {
            case OPS_ONLY -> false;
            case NONE -> WorkerSavedData.get(player.server).hasUnlock(player.getUUID(), stage);
            default -> hasStage(player, stage);
        };
    }

    /** Script tasks may declare themselves innate (known without a tome). */
    public static boolean scriptInnate(WorkerTask task) {
        return task instanceof it.ratlab.ratworker.api.InnateTask i && i.innate();
    }

    /** Highest unlocked level, 1..levels. Level n>1 needs levelStageTemplate (or op/free). */
    public static int level(ServerPlayer player, WorkerTask task) {
        int max = (int) number(task, TaskNumbers.LEVELS);
        int lvl = 1;
        boolean free = Settings.unlockMode() == UnlockMode.FREE || isOpBypass(player);
        for (int i = 2; i <= max; i++) {
            if (free || hasStage(player, levelStage(task, i))) lvl = i;
            else break;
        }
        return lvl;
    }

    /** Task default, then datapack, then server config (only when set to >= 0). */
    public static double number(WorkerTask task, String key) {
        double v = task.defaults().get(key, 0);
        Map<String, Double> dp = WorkerDataLoader.INSTANCE.data().taskNumbers().get(task.id());
        if (dp != null && dp.containsKey(key)) v = dp.get(key);
        ServerConfig.TaskSection s = section(task);
        if (s != null) {
            var cv = s.numbers().get(key);
            if (cv != null && cv.get() >= 0) v = cv.get();
        }
        return v;
    }
}
