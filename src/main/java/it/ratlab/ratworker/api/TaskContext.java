package it.ratlab.ratworker.api;

import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** What a running task can see and do. One context per worker per run. */
public interface TaskContext {
    WorkerEntity worker();

    ServerLevel level();

    /** Online owner, or null. */
    @Nullable
    ServerPlayer owner();

    /** Persistent per-run state, saved with the worker. */
    CompoundTag state();

    /** Resolved number (task default, then datapack, then server config). */
    double number(String key);

    /** Unlocked level of this task for the owner, 1..levels. */
    int taskLevel();

    /** Simulated press/hold/release of use, attack and sneak. Held inputs are released when the task stops. */
    WorkerInput input();

    /** Ticks since start (counts only ticks the task actually ran). */
    long ticksRunning();

    /** 1.0 normally; performance.lowSlowdown while a need is low. Multiply cooldowns by it. */
    double cooldownFactor();

    /** Walks toward a block with stuck detection. Call every tick until it stops returning MOVING. */
    MoveResult moveTo(BlockPos pos, double arriveDistance);

    /** Walks toward an entity with stuck detection. */
    MoveResult moveTo(Entity target, double arriveDistance);

    /** Ends the task. The reason is shown to the owner and in the command screen. */
    void stopTask(Component reason);

    /** Short status line for the command screen and Jade. */
    void setStatus(Component status);

    enum MoveResult { ARRIVED, MOVING, FAILED }
}
