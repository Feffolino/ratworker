package it.ratlab.ratworker.task.builtin;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Holds the block it was started on. Walks back when pushed away. Drains at the idle rate. */
public class StayTask implements WorkerTask {
    private static final ResourceLocation ID = RatWorker.id("stay");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.OAK_SIGN);
    }

    @Override
    public TaskNumbers defaults() {
        return BuiltinTasks.DEFAULTS.get("stay");
    }

    @Override
    public boolean isWork() {
        return false;
    }

    @Override
    public void start(TaskContext ctx) {
        ctx.state().putLong("Anchor", ctx.worker().blockPosition().asLong());
        ctx.worker().getNavigation().stop();
        ctx.setStatus(Component.translatable("ratworker.status.holding"));
    }

    @Override
    public void tick(TaskContext ctx) {
        WorkerEntity w = ctx.worker();
        BlockPos anchor = BlockPos.of(ctx.state().getLong("Anchor"));
        double dx = w.getX() - (anchor.getX() + 0.5), dz = w.getZ() - (anchor.getZ() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= ctx.number(TaskNumbers.RANGE) && Math.abs(w.getY() - anchor.getY()) < 1.5) {
            w.getNavigation().stop();
            ctx.setStatus(Component.translatable("ratworker.status.holding"));
            return;
        }
        TaskContext.MoveResult r = ctx.moveTo(anchor, 0.6);
        if (r == TaskContext.MoveResult.FAILED) ctx.stopTask(Component.translatable("ratworker.reason.cant_return"));
        else ctx.setStatus(Component.translatable("ratworker.status.returning"));
    }
}
