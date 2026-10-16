package it.ratlab.ratworker.supply;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The worker's supply container through the Forge item handler capability, so any inventory block works
 * (vanilla chests, barrels, Sophisticated Storage, Create vaults) without knowing the block.
 */
public final class SupplyAccess {
    /** Empty containers a worker returns to its supply on each run. */
    public static final TagKey<Item> EMPTY_CONTAINERS = TagKey.create(Registries.ITEM, RatWorker.id("empty_containers"));

    private SupplyAccess() {}

    public static boolean isContainer(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    @Nullable
    public static IItemHandler handler(WorkerEntity w) {
        BlockPos pos = w.supply();
        if (pos == null || !w.level().isLoaded(pos)) return null;
        BlockEntity be = w.level().getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
    }

    public static boolean inRange(WorkerEntity w) {
        BlockPos pos = w.supply();
        int r = ServerConfig.SUPPLY_RANGE.get();
        return pos != null && w.blockPosition().distSqr(pos) <= (double) r * r;
    }

    public static boolean contains(WorkerEntity w, Predicate<ItemStack> filter) {
        IItemHandler h = handler(w);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (filter.test(h.getStackInSlot(i))) return true;
        return false;
    }

    /** Moves up to {@code max} matching items into the worker's inventory. Returns how many moved. */
    public static int extract(WorkerEntity w, Predicate<ItemStack> filter, int max) {
        IItemHandler h = handler(w);
        if (h == null) return 0;
        int moved = 0;
        for (int i = 0; i < h.getSlots() && moved < max; i++) {
            ItemStack in = h.getStackInSlot(i);
            if (in.isEmpty() || !filter.test(in)) continue;
            ItemStack taken = h.extractItem(i, Math.min(max - moved, in.getCount()), false);
            if (taken.isEmpty()) continue;
            ItemStack rest = w.inventory().addItem(taken);
            moved += taken.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                h.insertItem(i, rest, false);
                break; // worker inventory full
            }
        }
        return moved;
    }

    /** Puts a stack into the supply. Returns what did not fit. For producing tasks (farm, phase 3). */
    public static ItemStack insert(WorkerEntity w, ItemStack stack) {
        IItemHandler h = handler(w);
        return h == null ? stack : ItemHandlerHelper.insertItemStacked(h, stack, false);
    }
}
