package it.ratlab.ratworker.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.script.ScriptType;
import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.skill.Manuals;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Server events:
 * <ul>
 *   <li>{@code WorkerEvents.registerTasks(e => e.create('chop')...)} on every server script load and /reload</li>
 *   <li>{@code WorkerEvents.gainXp(e => { e.amount = e.amount * 2 })}, cancel to give none</li>
 *   <li>{@code WorkerEvents.learn(e => { if (...) e.cancel() })} when a tome is used</li>
 * </ul>
 */
public final class WorkerEventsJS {
    public static final EventGroup GROUP = EventGroup.of("WorkerEvents");
    public static final EventHandler REGISTER_TASKS = GROUP.server("registerTasks", () -> RegisterTasks.class);
    public static final EventHandler GAIN_XP = GROUP.server("gainXp", () -> GainXp.class).hasResult();
    public static final EventHandler LEARN = GROUP.server("learn", () -> Learn.class).hasResult();
    public static final EventHandler MANUALS = GROUP.server("manuals", () -> RegisterManuals.class);

    private WorkerEventsJS() {}

    /** Clears script tasks and rebuilds them from WorkerEvents.registerTasks. */
    public static void reloadTasks() {
        WorkerTaskRegistry.INSTANCE.clearScripted();
        RegisterTasks e = new RegisterTasks();
        if (REGISTER_TASKS.hasListeners()) REGISTER_TASKS.post(ScriptType.SERVER, e);
        for (ScriptTaskBuilder b : e.builders) WorkerTaskRegistry.INSTANCE.registerScripted(b.build());
        RatWorker.LOG.info("Rat Worker: {} script tasks registered", e.builders.size());
        Manuals.clearScripted();
        if (MANUALS.hasListeners()) MANUALS.post(ScriptType.SERVER, new RegisterManuals());
    }

    /** Returns the XP to grant, 0 if a script cancelled it. */
    public static double gainXp(WorkerEntity w, @Nullable WorkerTask task, double amount) {
        if (!GAIN_XP.hasListeners()) return amount;
        GainXp e = new GainXp(w, task == null ? "" : task.id().toString(), amount);
        if (GAIN_XP.post(ScriptType.SERVER, e).interruptFalse()) return 0;
        return Math.max(0, e.amount);
    }

    public static boolean learn(WorkerEntity w, WorkerTask task, ServerPlayer player) {
        if (!LEARN.hasListeners()) return true;
        return !LEARN.post(ScriptType.SERVER, new Learn(w, task.id().toString(), player)).interruptFalse();
    }

    public static class RegisterTasks extends EventJS {
        final List<ScriptTaskBuilder> builders = new ArrayList<>();

        /** 'chop' becomes kubejs:chop; 'mypack:chop' is kept. */
        public ScriptTaskBuilder create(String id) {
            ResourceLocation rl = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("kubejs", id);
            ScriptTaskBuilder b = new ScriptTaskBuilder(rl);
            builders.add(b);
            return b;
        }
    }

    /** {@code WorkerEvents.manuals(e => e.add('ratlab:manual_crank', 'ratworker:crank', 0))}: any item teaches a task. */
    public static class RegisterManuals extends EventJS {
        /** xp -1 = the task's learnXp. */
        public void add(String item, String task, int xp) {
            ResourceLocation t = task.contains(":") ? ResourceLocation.parse(task) : ResourceLocation.fromNamespaceAndPath("ratworker", task);
            Manuals.registerScripted(ResourceLocation.parse(item), t, xp);
        }

        public void add(String item, String task) {
            add(item, task, -1);
        }
    }

    public static class GainXp extends EventJS {
        public final WorkerEntity worker;
        public final String task;
        public double amount;

        GainXp(WorkerEntity worker, String task, double amount) {
            this.worker = worker;
            this.task = task;
            this.amount = amount;
        }
    }

    public static class Learn extends EventJS {
        public final WorkerEntity worker;
        public final String task;
        public final ServerPlayer player;

        Learn(WorkerEntity worker, String task, ServerPlayer player) {
            this.worker = worker;
            this.task = task;
            this.player = player;
        }
    }
}
