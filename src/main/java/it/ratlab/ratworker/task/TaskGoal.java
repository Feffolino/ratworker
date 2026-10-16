package it.ratlab.ratworker.task;

import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Bridges the vanilla goal selector to the worker's {@link TaskRunner}. */
public class TaskGoal extends Goal {
    private final WorkerEntity worker;

    public TaskGoal(WorkerEntity worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return worker.runner().isRunning();
    }

    @Override
    public boolean canContinueToUse() {
        return worker.runner().isRunning();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        worker.runner().tick();
    }

    @Override
    public void stop() {
        // preempted (e.g. by a supply run): let go of held inputs, the task re-holds when it resumes
        worker.input().releaseAll();
        worker.getNavigation().stop();
    }
}
