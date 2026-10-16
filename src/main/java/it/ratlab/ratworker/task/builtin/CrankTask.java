package it.ratlab.ratworker.task.builtin;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerInput;
import it.ratlab.ratworker.api.WorkerInput.Action;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.skill.Branch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Walks to the nearest hand crank within range (block tag #ratworker:crank_targets, default create:hand_crank,
 * which has a block entity) and holds use on it.
 * Create's HandCrankBlock.use() calls HandCrankBlockEntity.turn(sneaking), which keeps it spinning for
 * 10 ticks; WorkerInput re-sends use every holdIntervalTicks (default 4), so the crank never stops.
 * No Create classes are used: this is a plain player interaction. Registered only when Create is loaded.
 */
public class CrankTask implements WorkerTask {
    private static final ResourceLocation ID = RatWorker.id("crank");
    public static final TagKey<Block> TARGETS = TagKey.create(Registries.BLOCK, RatWorker.id("crank_targets"));
    private static final double WORK_DISTANCE = 3.0; // eye to crank center, inside WorkerInput.REACH

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public ItemStack icon() {
        Item crank = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("create", "hand_crank"));
        return new ItemStack(crank == null || crank == Items.AIR ? Items.STICK : crank);
    }

    @Override
    public TaskNumbers defaults() {
        return BuiltinTasks.DEFAULTS.get("crank");
    }

    @Override
    public java.util.List<Branch> branches() {
        return java.util.List.of(Branch.HUNGER, Branch.THIRST, Branch.SPEED);
    }

    @Override
    public TaskCheck canStart(TaskContext ctx) {
        int range = (int) ctx.number(TaskNumbers.RANGE);
        return find(ctx.worker(), range) == null ? TaskCheck.fail("ratworker.reason.no_crank", range) : TaskCheck.OK;
    }

    @Override
    public void start(TaskContext ctx) {
        BlockPos pos = find(ctx.worker(), (int) ctx.number(TaskNumbers.RANGE));
        if (pos == null) {
            ctx.stopTask(Component.translatable("ratworker.reason.no_crank", (int) ctx.number(TaskNumbers.RANGE)));
            return;
        }
        ctx.state().putLong("Crank", pos.asLong());
        ctx.setStatus(Component.translatable("ratworker.status.to_crank"));
    }

    @Override
    public void tick(TaskContext ctx) {
        if (!ctx.state().contains("Crank")) return;
        WorkerEntity w = ctx.worker();
        BlockPos pos = BlockPos.of(ctx.state().getLong("Crank"));
        if (!w.level().getBlockState(pos).is(TARGETS)) {
            ctx.stopTask(Component.translatable("ratworker.reason.crank_gone"));
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        if (w.getEyePosition().distanceTo(center) > WORK_DISTANCE) {
            ctx.input().release(Action.USE);
            TaskContext.MoveResult r = ctx.moveTo(pos, 1.8);
            if (r == TaskContext.MoveResult.FAILED) ctx.stopTask(Component.translatable("ratworker.reason.cant_reach_crank"));
            else ctx.setStatus(Component.translatable("ratworker.status.to_crank"));
            return;
        }
        w.getNavigation().stop();
        w.getLookControl().setLookAt(center.x, center.y, center.z);
        boolean reverse = ctx.number("reverse") > 0;
        if (reverse && !ctx.input().isHeld(Action.SNEAK)) ctx.input().hold(Action.SNEAK, WorkerInput.Target.inFront());
        // default clicks are slow (gaps in rotation); the speed branch brings them down to minHoldInterval
        int interval = (int) Math.max(ctx.number("minHoldInterval"),
                ctx.number("holdInterval") - Branch.SPEED.perLevel() * w.skills().level(Branch.SPEED, this));
        if (!ctx.input().hold(Action.USE, WorkerInput.Target.block(pos), interval)) {
            ctx.stopTask(Component.translatable("ratworker.reason.cant_use_crank"));
            return;
        }
        ctx.setStatus(Component.translatable("ratworker.status.cranking"));
    }

    @Override
    public void stop(TaskContext ctx) {
        ctx.input().releaseAll();
        ctx.worker().getNavigation().stop();
    }

    /**
     * Nearest crank within the radius (sphere) around the worker. Cranks have a block entity, so only the
     * block entities of the few loaded chunks around are checked, not every block.
     */
    @Nullable
    static BlockPos find(WorkerEntity w, int range) {
        BlockPos origin = w.blockPosition();
        double max = (double) range * range;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int c0x = (origin.getX() - range) >> 4, c1x = (origin.getX() + range) >> 4;
        int c0z = (origin.getZ() - range) >> 4, c1z = (origin.getZ() + range) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                if (!w.level().hasChunk(cx, cz)) continue;
                for (BlockPos p : w.level().getChunk(cx, cz).getBlockEntitiesPos()) {
                    double d = p.distSqr(origin);
                    if (d > max || d >= bestDist || !w.level().getBlockState(p).is(TARGETS)) continue;
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }
}
