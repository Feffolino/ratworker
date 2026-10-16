package it.ratlab.ratworker.api;

import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A job a worker can run. Implementations are stateless singletons: per-worker state lives in
 * {@link TaskContext#state()}, which is saved with the entity, so tasks survive reloads.
 * <p>
 * Register through {@link RegisterWorkerTasksEvent} on your mod bus.
 */
public interface WorkerTask {
    ResourceLocation id();

    /** Lang key base: task.&lt;namespace&gt;.&lt;path&gt; (+ ".desc"). */
    default String langKey() {
        return "task." + id().getNamespace() + "." + id().getPath();
    }

    default Component title() {
        return Component.translatable(langKey());
    }

    default Component description() {
        return Component.translatable(langKey() + ".desc");
    }

    ItemStack icon();

    /** Built-in numbers; overridden by datapack and server config. */
    default TaskNumbers defaults() {
        return TaskNumbers.of(1, 1, 1, 16, 1);
    }

    /** Stage that unlocks the task; null = unlocking.stageTemplate. */
    default String stageKey() {
        return null;
    }

    /** Extra unlock condition on top of the stage check. */
    default boolean isUnlocked(ServerPlayer player, WorkerEntity worker) {
        return true;
    }

    /** Checked before start. Return a reason the player can read when the task cannot run. */
    default TaskCheck canStart(TaskContext ctx) {
        return TaskCheck.OK;
    }

    void start(TaskContext ctx);

    /** Called every performance.taskTickInterval ticks while running. Never block here. */
    void tick(TaskContext ctx);

    /** Called once when the task ends for any reason. Release held inputs, clear navigation. */
    default void stop(TaskContext ctx) {}

    /** Upgrade branches of this task (bought with worker XP). SPEED only where the task uses it. */
    default java.util.List<it.ratlab.ratworker.skill.Branch> branches() {
        return java.util.List.of(it.ratlab.ratworker.skill.Branch.HUNGER, it.ratlab.ratworker.skill.Branch.THIRST);
    }

    /** Working tasks use the 'working' drain rate. Idle-like tasks (stay) can return false. */
    default boolean isWork() {
        return true;
    }
}
