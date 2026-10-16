package it.ratlab.ratworker.supply;

import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.entity.Drinks;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.entity.WorkerNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Supply run: when a need is below its threshold and the inventory has nothing for it, the worker pauses its
 * task (higher goal priority), walks to the supply container, takes food/drink, and goes back to work.
 */
public class SupplyGoal extends Goal {
    private static final int TIMEOUT = 600, RETRY_AFTER = 1200;

    private final WorkerEntity worker;
    private int ticks;
    private long nextCheck;
    private boolean done;

    public SupplyGoal(WorkerEntity worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        long now = worker.level().getGameTime();
        if (now < nextCheck) return false;
        nextCheck = now + 40;
        if (worker.supply() == null || !Settings.enabled() || !worker.needs().wantsSupplies(worker) || !SupplyAccess.inRange(worker)) return false;
        boolean any = SupplyAccess.contains(worker, this::wanted);
        return any; // nothing useful there yet: checked again in 2 s, so new items are picked up quickly
    }

    private boolean wanted(ItemStack s) {
        WorkerNeeds n = worker.needs();
        boolean food = Settings.hungerEnabled() && n.hunger() < ServerConfig.EAT_BELOW.get() && WorkerNeeds.isFood(s);
        boolean drink = Settings.thirstEnabled() && n.thirst() < ServerConfig.DRINK_BELOW.get() && WorkerNeeds.isDrink(s);
        return food || drink;
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        worker.input().releaseAll();
        worker.runner().setStatus(Component.translatable("ratworker.status.fetching"));
    }

    @Override
    public boolean canContinueToUse() {
        return !done && ticks < TIMEOUT && worker.supply() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos pos = worker.supply();
        if (pos == null) return;
        Vec3 c = Vec3.atCenterOf(pos);
        worker.getLookControl().setLookAt(c.x, c.y, c.z);
        if (worker.getEyePosition().distanceTo(c) > 2.8) {
            if (ticks % 20 == 1) worker.getNavigation().moveTo(c.x, c.y, c.z, 1.0);
            return;
        }
        worker.getNavigation().stop();
        // leave empty bottles, buckets, bowls and canteens in the supply for refilling
        for (int i = 0; i < worker.inventory().getContainerSize(); i++) {
            ItemStack s = worker.inventory().getItem(i);
            if (s.is(SupplyAccess.EMPTY_CONTAINERS)) worker.inventory().setItem(i, SupplyAccess.insert(worker, s));
        }
        int take = ServerConfig.SUPPLY_TAKE.get();
        WorkerNeeds n = worker.needs();
        if (Settings.hungerEnabled() && n.hunger() < ServerConfig.EAT_BELOW.get() && !n.hasFood(worker)) {
            SupplyAccess.extract(worker, WorkerNeeds::isFood, take);
        }
        if (Settings.thirstEnabled() && n.thirst() < ServerConfig.DRINK_BELOW.get() && !n.hasDrink(worker)) {
            // cleanest water first
            for (int purity = 2; purity >= 0; purity--) {
                int p = purity;
                if (SupplyAccess.extract(worker, s -> {
                    Drinks.Info i = Drinks.of(s);
                    return i != null && i.purity() == p;
                }, take) > 0) break;
            }
        }
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        done = true;
    }

    @Override
    public void stop() {
        worker.getNavigation().stop();
        if (!done) nextCheck = worker.level().getGameTime() + RETRY_AFTER; // could not reach it, try later
    }
}
