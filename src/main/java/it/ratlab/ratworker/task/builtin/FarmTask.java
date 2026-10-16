package it.ratlab.ratworker.task.builtin;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.skill.Branch;
import it.ratlab.ratworker.supply.SupplyAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Harvests ripe crops, replants, plants empty farmland and (optionally) tills dirt next to farmland or water,
 * inside a square of radius {@code range} around the block it was started on. Seeds come from the inventory
 * (or the supply), produce goes to the supply once {@code depositAt} items pile up.
 * The field is scanned a few blocks per tick (performance.maxBlockScansPerTick), never all at once.
 */
public class FarmTask implements WorkerTask {
    private static final ResourceLocation ID = RatWorker.id("farm");
    private static final int HARVEST = 1, PLANT = 2, TILL = 3, STORE = 4;

    @Override public ResourceLocation id() { return ID; }
    @Override public ItemStack icon() { return new ItemStack(Items.IRON_HOE); }
    @Override public TaskNumbers defaults() { return BuiltinTasks.DEFAULTS.get("farm"); }
    @Override public List<Branch> branches() { return List.of(Branch.HUNGER, Branch.THIRST, Branch.SPEED); }

    @Override
    public void start(TaskContext ctx) {
        ctx.state().putLong("Anchor", ctx.worker().blockPosition().asLong());
        ctx.state().putInt("Cursor", 0);
        ctx.setStatus(Component.translatable("ratworker.status.farm_scanning"));
    }

    @Override
    public void tick(TaskContext ctx) {
        WorkerEntity w = ctx.worker();
        var st = ctx.state();
        long now = w.level().getGameTime();
        if (now < st.getLong("Sleep")) return;

        int job = st.getInt("Job");
        if (job == 0) {
            if (needsStoring(ctx)) {
                st.putInt("Job", STORE);
                return;
            }
            scan(ctx);
            return;
        }
        if (job == STORE) {
            store(ctx);
            return;
        }
        BlockPos target = BlockPos.of(st.getLong("Target"));
        Vec3 c = Vec3.atCenterOf(target);
        if (w.getEyePosition().distanceTo(c) > 3.5) {
            if (ctx.moveTo(target, 1.5) == TaskContext.MoveResult.FAILED) clearJob(st); // skip this spot
            ctx.setStatus(Component.translatable("ratworker.status.farm_working"));
            return;
        }
        w.getNavigation().stop();
        w.getLookControl().setLookAt(c.x, c.y, c.z);
        if (now < st.getLong("Next")) return;
        double speed = Math.max(0.2, 1.0 - 0.1 * ctx.worker().skills().level(Branch.SPEED, this));
        st.putLong("Next", now + Math.round(ctx.number("actionCooldown") * ctx.cooldownFactor() * speed));
        switch (job) {
            case HARVEST -> harvest(ctx, target);
            case PLANT -> plant(ctx, target);
            case TILL -> till(ctx, target);
            default -> {}
        }
        clearJob(st);
    }

    @Override
    public void stop(TaskContext ctx) {
        ctx.input().releaseAll();
        ctx.worker().getNavigation().stop();
    }

    // ---- scanning ----

    private void scan(TaskContext ctx) {
        WorkerEntity w = ctx.worker();
        Level level = w.level();
        BlockPos anchor = BlockPos.of(ctx.state().getLong("Anchor"));
        int r = (int) ctx.number(TaskNumbers.RANGE);
        int side = 2 * r + 1, layers = 5, total = side * side * layers;
        int cursor = ctx.state().getInt("Cursor");
        boolean canTill = ctx.number("till") > 0 && hoeSlot(w) >= 0;
        boolean hasSeeds = seedSlot(w, null) >= 0;
        int budget = ServerConfig.MAX_BLOCK_SCANS.get();
        for (int i = 0; i < budget; i++, cursor++) {
            if (cursor >= total) {
                cursor = 0;
                ctx.state().putInt("Cursor", 0);
                ctx.state().putLong("Sleep", level.getGameTime() + 100); // field done, look again in 5 s
                ctx.setStatus(Component.translatable("ratworker.status.farm_idle"));
                return;
            }
            int dy = cursor / (side * side) - 2, rest = cursor % (side * side);
            BlockPos p = anchor.offset(rest % side - r, dy, rest / side - r);
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            int job = 0;
            if (s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) job = HARVEST;
            else if (s.is(Blocks.FARMLAND) && level.isEmptyBlock(p.above()) && hasSeeds) job = PLANT;
            else if (canTill && tillable(level, p)) job = TILL;
            if (job != 0) {
                ctx.state().putInt("Cursor", cursor + 1);
                ctx.state().putInt("Job", job);
                ctx.state().putLong("Target", p.asLong());
                return;
            }
        }
        ctx.state().putInt("Cursor", cursor);
        ctx.setStatus(Component.translatable("ratworker.status.farm_scanning"));
    }

    private static boolean tillable(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!(s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT_PATH)) || !level.isEmptyBlock(p.above())) return false;
        // grow the field from existing farmland or water only, never the whole area
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = p.relative(d);
            if (level.getBlockState(n).is(Blocks.FARMLAND) || level.getFluidState(n).is(Fluids.WATER)) return true;
        }
        return false;
    }

    private static void clearJob(net.minecraft.nbt.CompoundTag st) {
        st.putInt("Job", 0);
    }

    // ---- actions ----

    private void harvest(TaskContext ctx, BlockPos p) {
        WorkerEntity w = ctx.worker();
        BlockState s = w.level().getBlockState(p);
        if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) return;
        Item seed = crop.getCloneItemStack(w.level(), p, s).getItem();
        if (!ctx.input().breakNow(p)) return;
        w.pickupItems(3.0);
        int slot = seedSlot(w, seed);
        if (slot >= 0 && w.level().getBlockState(p.below()).is(Blocks.FARMLAND)) ctx.input().useSlot(slot, p.below(), Direction.UP);
        ctx.setStatus(Component.translatable("ratworker.status.farm_working"));
    }

    private void plant(TaskContext ctx, BlockPos farmland) {
        WorkerEntity w = ctx.worker();
        if (!w.level().getBlockState(farmland).is(Blocks.FARMLAND) || !w.level().isEmptyBlock(farmland.above())) return;
        int slot = seedSlot(w, null);
        if (slot >= 0) ctx.input().useSlot(slot, farmland, Direction.UP);
    }

    private void till(TaskContext ctx, BlockPos p) {
        WorkerEntity w = ctx.worker();
        int slot = hoeSlot(w);
        if (slot >= 0 && tillable(w.level(), p)) ctx.input().useSlot(slot, p, Direction.UP);
    }

    // ---- supply ----

    private boolean needsStoring(TaskContext ctx) {
        WorkerEntity w = ctx.worker();
        if (w.supply() == null || !SupplyAccess.inRange(w)) return false;
        int produce = 0;
        SimpleContainer inv = w.inventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isProduce(inv.getItem(i))) produce += inv.getItem(i).getCount();
        boolean noSeeds = seedSlot(w, null) < 0 && SupplyAccess.contains(w, FarmTask::isSeed);
        return produce >= ctx.number("depositAt") || noSeeds || inv.getItem(inv.getContainerSize() - 1).getCount() > 0 && produce > 0;
    }

    private void store(TaskContext ctx) {
        WorkerEntity w = ctx.worker();
        BlockPos pos = w.supply();
        if (pos == null) {
            clearJob(ctx.state());
            return;
        }
        ctx.setStatus(Component.translatable("ratworker.status.farm_storing"));
        if (w.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > 2.8) {
            if (ctx.moveTo(pos, 1.5) == TaskContext.MoveResult.FAILED) clearJob(ctx.state());
            return;
        }
        w.getNavigation().stop();
        SimpleContainer inv = w.inventory();
        boolean keptSeed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (isSeed(s) && !keptSeed) { keptSeed = true; continue; } // keep one stack to replant
            if (isProduce(s) || isSeed(s)) inv.setItem(i, SupplyAccess.insert(w, s));
        }
        if (seedSlot(w, null) < 0) SupplyAccess.extract(w, FarmTask::isSeed, 32);
        w.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        clearJob(ctx.state());
    }

    // ---- items ----

    /** Plants a CropBlock (wheat seeds, carrots, potatoes, beetroot seeds, modded crops). */
    static boolean isSeed(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    /** Harvest output that goes to the supply: crop drops that are not seeds, plus spare seeds. */
    private static boolean isProduce(ItemStack s) {
        return !s.isEmpty() && !(s.getItem() instanceof HoeItem) && (s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.POISONOUS_POTATO)
                || s.is(net.minecraftforge.common.Tags.Items.CROPS));
    }

    private static int seedSlot(WorkerEntity w, Item preferred) {
        SimpleContainer inv = w.inventory();
        int any = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isSeed(s)) continue;
            if (preferred == null || s.is(preferred)) return i;
            if (any < 0) any = i;
        }
        return any;
    }

    private static int hoeSlot(WorkerEntity w) {
        SimpleContainer inv = w.inventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof HoeItem) return i;
        return -1;
    }
}
