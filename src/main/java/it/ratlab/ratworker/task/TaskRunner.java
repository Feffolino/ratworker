package it.ratlab.ratworker.task;

import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerInput;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.world.WorkerTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Runs the current task of one worker and implements {@link TaskContext} for it. Server side. */
public class TaskRunner implements TaskContext {
    private final WorkerEntity worker;
    @Nullable private WorkerTask task;
    @Nullable private ResourceLocation pendingId; // loaded from NBT, resolved on first tick
    private CompoundTag state = new CompoundTag();
    private long ticksRunning;
    private int taskLevel = 1;
    private Component status = Component.empty();
    private Component lastStop = Component.empty();
    private boolean paused;

    // stuck detection
    @Nullable private Vec3 moveTarget;
    private Vec3 checkpoint = Vec3.ZERO;
    private long checkpointTime, lastPathTime;
    private int failures;

    public TaskRunner(WorkerEntity worker) {
        this.worker = worker;
    }

    public boolean isRunning() {
        return task != null || pendingId != null;
    }

    public boolean isPaused() {
        return paused;
    }

    @Nullable
    public WorkerTask task() {
        return task;
    }

    public Component status() {
        return status;
    }

    public Component lastStop() {
        return lastStop;
    }

    /** Full availability check used by the command screen and by start. */
    public TaskCheck check(WorkerTask t, @Nullable ServerPlayer by) {
        if (!Settings.enabled()) return TaskCheck.fail("ratworker.reason.disabled");
        if (!TaskRules.enabled(t)) return TaskCheck.fail("ratworker.reason.task_disabled");
        if (worker.needs().anyEmpty()) return TaskCheck.fail(worker.needs().emptyReason());
        if (!isRunning() && WorkerTracker.activeCount() >= Settings.maxActiveWorkers()) return TaskCheck.fail("ratworker.reason.server_limit");
        TaskCheck own = t.canStart(this);
        return own == null ? TaskCheck.OK : own;
    }

    public TaskCheck start(WorkerTask t, @Nullable ServerPlayer by) {
        TaskCheck c = check(t, by);
        if (!c.ok()) return c;
        if (task != null) stopInternal(null);
        task = t;
        pendingId = null;
        state = new CompoundTag();
        ticksRunning = 0;
        taskLevel = by != null ? TaskRules.level(by, t) : 1;
        status = t.title();
        lastStop = Component.empty();
        resetMove();
        t.start(this);
        worker.syncTask();
        return TaskCheck.OK;
    }

    /** Stops by player request or by a reason. A null reason means a silent stop. */
    public void stop(@Nullable Component reason) {
        if (task == null && pendingId == null) return;
        stopInternal(reason);
    }

    private void stopInternal(@Nullable Component reason) {
        WorkerTask t = task;
        if (t != null) t.stop(this);
        worker.input().releaseAll();
        task = null;
        pendingId = null;
        state = new CompoundTag();
        paused = false;
        worker.getNavigation().stop();
        resetMove();
        status = Component.empty();
        if (reason != null) {
            lastStop = reason;
            ServerPlayer owner = owner();
            if (owner != null) owner.displayClientMessage(Component.translatable("ratworker.msg.stopped", worker.getName(), reason), false);
        }
        worker.syncTask();
    }

    /** Called every tick by TaskGoal. */
    public void tick() {
        if (pendingId != null) {
            task = WorkerTaskRegistry.INSTANCE.get(pendingId);
            if (task == null) {
                Component r = Component.translatable("ratworker.reason.unknown_task", pendingId.toString());
                pendingId = null;
                stopInternal(r);
                return;
            }
            pendingId = null;
            worker.syncTask();
        }
        if (task == null) return;

        paused = false;
        if (!Settings.enabled()) {
            pause(Component.translatable("ratworker.reason.disabled"));
            return;
        }
        if (!ServerConfig.WORK_OFFLINE.get() && owner() == null) {
            pause(Component.translatable("ratworker.status.owner_offline"));
            return;
        }
        if (worker.needs().anyEmpty()) {
            stopInternal(worker.needs().emptyReason());
            return;
        }
        ticksRunning++;
        int interval = ServerConfig.TASK_TICK_INTERVAL.get();
        if (worker.tickCount % interval == 0) task.tick(this);
    }

    private void pause(Component why) {
        paused = true;
        status = why;
        worker.getNavigation().stop();
        worker.input().releaseAll();
    }

    // ---- TaskContext ----

    @Override
    public WorkerEntity worker() {
        return worker;
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) worker.level();
    }

    @Override
    @Nullable
    public ServerPlayer owner() {
        return worker.getOwnerId() == null ? null : level().getServer().getPlayerList().getPlayer(worker.getOwnerId());
    }

    @Override
    public CompoundTag state() {
        return state;
    }

    @Override
    public double number(String key) {
        return task == null ? 0 : TaskRules.number(task, key);
    }

    @Override
    public int taskLevel() {
        return taskLevel;
    }

    @Override
    public WorkerInput input() {
        return worker.input();
    }

    @Override
    public long ticksRunning() {
        return ticksRunning;
    }

    @Override
    public double cooldownFactor() {
        return worker.needs().anyLow() ? ServerConfig.LOW_SLOWDOWN.get() : 1.0;
    }

    @Override
    public MoveResult moveTo(BlockPos pos, double arriveDistance) {
        Vec3 target = Vec3.atBottomCenterOf(pos);
        return track(target, arriveDistance, () -> worker.getNavigation().moveTo(target.x, target.y, target.z, speed()));
    }

    @Override
    public MoveResult moveTo(Entity target, double arriveDistance) {
        return track(target.position(), arriveDistance, () -> worker.getNavigation().moveTo(target, speed()));
    }

    @Override
    public void stopTask(Component reason) {
        stopInternal(reason);
    }

    @Override
    public void setStatus(Component status) {
        this.status = status;
    }

    private double speed() {
        return number(TaskNumbers.SPEED);
    }

    private interface PathStarter {
        boolean start();
    }

    private MoveResult track(Vec3 target, double arrive, PathStarter starter) {
        long now = worker.level().getGameTime();
        if (worker.position().distanceTo(target) <= arrive) {
            worker.getNavigation().stop();
            resetMove();
            return MoveResult.ARRIVED;
        }
        if (moveTarget == null || moveTarget.distanceTo(target) > 2.0) {
            moveTarget = target;
            failures = 0;
            checkpoint = worker.position();
            checkpointTime = now;
            lastPathTime = Long.MIN_VALUE / 2;
        }
        moveTarget = target;
        int recompute = ServerConfig.PATH_RECOMPUTE.get();
        if (worker.getNavigation().isDone() || now - lastPathTime >= recompute) {
            lastPathTime = now;
            if (!starter.start()) failures++;
        }
        if (now - checkpointTime >= ServerConfig.STUCK_TICKS.get()) {
            if (worker.position().distanceTo(checkpoint) < 1.0) failures++;
            else failures = Math.max(0, failures - 1);
            checkpoint = worker.position();
            checkpointTime = now;
        }
        if (failures >= ServerConfig.MAX_PATH_FAILURES.get()) {
            failures = 0;
            return MoveResult.FAILED;
        }
        return MoveResult.MOVING;
    }

    private void resetMove() {
        moveTarget = null;
        failures = 0;
    }

    // ---- persistence ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ResourceLocation id = task != null ? task.id() : pendingId;
        if (id != null) {
            tag.putString("Task", id.toString());
            tag.put("State", state.copy());
            tag.putLong("Ticks", ticksRunning);
            tag.putInt("Level", taskLevel);
        }
        tag.putString("LastStop", Component.Serializer.toJson(lastStop));
        return tag;
    }

    public void load(CompoundTag tag) {
        task = null;
        pendingId = tag.contains("Task") ? ResourceLocation.tryParse(tag.getString("Task")) : null;
        state = tag.getCompound("State");
        ticksRunning = tag.getLong("Ticks");
        taskLevel = Math.max(1, tag.getInt("Level"));
        Component c = tag.contains("LastStop") ? Component.Serializer.fromJson(tag.getString("LastStop")) : null;
        lastStop = c == null ? Component.empty() : c;
    }

    /** Task id for client sync, including a not yet resolved one. */
    public String syncedId() {
        ResourceLocation id = task != null ? task.id() : pendingId;
        return id == null ? "" : id.toString();
    }
}
