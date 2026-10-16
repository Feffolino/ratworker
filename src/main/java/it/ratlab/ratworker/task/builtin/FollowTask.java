package it.ratlab.ratworker.task.builtin;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Follows the owner, keeps a distance, teleports next to them when too far. */
public class FollowTask implements WorkerTask {
    private static final ResourceLocation ID = RatWorker.id("follow");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.LEAD);
    }

    @Override
    public TaskNumbers defaults() {
        return BuiltinTasks.DEFAULTS.get("follow");
    }

    @Override
    public TaskCheck canStart(TaskContext ctx) {
        ServerPlayer owner = ctx.owner();
        if (owner == null) return TaskCheck.fail("ratworker.status.owner_offline");
        if (owner.level() != ctx.worker().level()) return TaskCheck.fail("ratworker.reason.other_dimension");
        return TaskCheck.OK;
    }

    @Override
    public void start(TaskContext ctx) {
        ctx.setStatus(Component.translatable("ratworker.status.following"));
    }

    @Override
    public void tick(TaskContext ctx) {
        WorkerEntity w = ctx.worker();
        ServerPlayer owner = ctx.owner();
        if (owner == null || owner.isSpectator()) {
            w.getNavigation().stop();
            return;
        }
        if (owner.level() != w.level()) {
            w.getNavigation().stop();
            ctx.setStatus(Component.translatable("ratworker.reason.other_dimension"));
            return;
        }
        w.getLookControl().setLookAt(owner, 10f, w.getMaxHeadXRot());
        double dist = w.distanceTo(owner);
        if (dist >= ctx.number("teleportDistance")) {
            teleportNear(w, owner);
            return;
        }
        boolean moving = ctx.state().getBoolean("Moving");
        if (!moving && dist > ctx.number(TaskNumbers.RANGE)) moving = true;
        if (moving) {
            TaskContext.MoveResult r = ctx.moveTo(owner, ctx.number("stopDistance"));
            if (r == TaskContext.MoveResult.ARRIVED) moving = false;
            else if (r == TaskContext.MoveResult.FAILED && !teleportNear(w, owner)) {
                ctx.setStatus(Component.translatable("ratworker.status.cant_reach"));
            }
        }
        ctx.state().putBoolean("Moving", moving);
        if (!moving) ctx.setStatus(Component.translatable("ratworker.status.following"));
    }

    @Override
    public void stop(TaskContext ctx) {
        ctx.worker().getNavigation().stop();
    }

    /** Same idea as vanilla pets: a few random walkable spots around the owner. */
    static boolean teleportNear(WorkerEntity w, ServerPlayer owner) {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = w.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = w.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            int dy = w.getRandom().nextIntBetweenInclusive(-1, 1);
            BlockPos p = base.offset(dx, dy, dz);
            if (WalkNodeEvaluator.getBlockPathTypeStatic(w.level(), p.mutable()) != BlockPathTypes.WALKABLE) continue;
            var to = net.minecraft.world.phys.Vec3.atBottomCenterOf(p);
            if (!w.level().noCollision(w, w.getBoundingBox().move(to.subtract(w.position())))) continue;
            w.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, w.getYRot(), w.getXRot());
            w.getNavigation().stop();
            return true;
        }
        return false;
    }
}
