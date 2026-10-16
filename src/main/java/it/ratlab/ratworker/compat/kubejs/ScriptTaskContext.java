package it.ratlab.ratworker.compat.kubejs;

import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.WorkerInput;
import it.ratlab.ratworker.api.WorkerInput.Action;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.skill.Branch;
import it.ratlab.ratworker.supply.SupplyAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

/**
 * What a KubeJS task sees as {@code ctx}. Wraps {@link TaskContext} with script-friendly helpers.
 * Every interaction goes through WorkerInput (fake player with the owner's rights), so claims apply.
 */
public class ScriptTaskContext {
    private final TaskContext ctx;

    ScriptTaskContext(TaskContext ctx) {
        this.ctx = ctx;
    }

    public WorkerEntity getWorker() { return ctx.worker(); }
    public ServerLevel getLevel() { return ctx.level(); }
    @Nullable public ServerPlayer getOwner() { return ctx.owner(); }
    /** Saved with the worker; survives reloads. */
    public CompoundTag getState() { return ctx.state(); }
    public SimpleContainer getInventory() { return ctx.worker().inventory(); }
    public double number(String key) { return ctx.number(key); }
    public long getTicks() { return ctx.ticksRunning(); }
    public float getHunger() { return ctx.worker().needs().hunger(); }
    public float getThirst() { return ctx.worker().needs().thirst(); }
    /** Level of a branch of this task: 'hunger', 'thirst', 'speed' (or the worker's 'health', 'strength'). */
    public int branch(String key) {
        Branch b = Branch.fromKey(key);
        return b == null ? 0 : ctx.worker().skills().level(b, ctx.worker().runner().task());
    }
    /** 1 normally, higher while hungry/thirsty. Multiply your cooldowns by it. */
    public double getCooldownFactor() { return ctx.cooldownFactor(); }

    /** True once every {@code ticks} (scaled by cooldownFactor and the speed branch); stored in state. */
    public boolean cooldown(String key, int ticks) {
        long now = ctx.level().getGameTime();
        String k = "cd_" + key;
        if (now < ctx.state().getLong(k)) return false;
        double speed = Math.max(0.2, 1.0 - 0.1 * branch("speed"));
        ctx.state().putLong(k, now + Math.max(1, Math.round(ticks * ctx.cooldownFactor() * speed)));
        return true;
    }

    public String moveTo(BlockPos pos) { return moveTo(pos, 1.5); }

    /** "arrived", "moving" or "failed" (stuck or no path several times). */
    public String moveTo(BlockPos pos, double distance) {
        return ctx.moveTo(pos, distance).name().toLowerCase(Locale.ROOT);
    }

    public void stopTask(Component reason) { ctx.stopTask(reason); }
    public void setStatus(Component status) { ctx.setStatus(status); }

    // ---- input simulation ----
    public boolean holdUse(BlockPos pos) { return ctx.input().hold(Action.USE, WorkerInput.Target.block(pos)); }
    public boolean holdAttack(BlockPos pos) { return ctx.input().hold(Action.ATTACK, WorkerInput.Target.block(pos)); }
    public boolean holdSneak() { return ctx.input().hold(Action.SNEAK, WorkerInput.Target.inFront()); }
    public boolean press(String action, BlockPos pos) { return ctx.input().press(action(action), WorkerInput.Target.block(pos)); }
    public void release(String action) { ctx.input().release(action(action)); }
    public void releaseAll() { ctx.input().releaseAll(); }
    public boolean isHeld(String action) { return ctx.input().isHeld(action(action)); }

    /** Right click on a block with the main-hand item. */
    public boolean useItemOn(BlockPos pos) { return press("use", pos); }

    /** Right click on a block with the item of an inventory slot. */
    public boolean useItemOn(BlockPos pos, int slot) {
        WorkerEntity w = ctx.worker();
        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        net.minecraft.core.Direction face = net.minecraft.core.Direction.getNearest(w.getEyePosition().x - c.x, w.getEyePosition().y - c.y, w.getEyePosition().z - c.z);
        return ctx.input().useSlot(slot, pos, face);
    }

    /** Breaks the block now with the main-hand tool (drops fall as items). */
    public boolean breakBlock(BlockPos pos) { return ctx.input().breakNow(pos); }

    /** Picks up dropped items within radius into the inventory. Returns items picked. */
    public int pickupItems(double radius) {
        return ctx.worker().pickupItems(radius);
    }

    /** Attacks the nearest living entity of the entity type tag in reach (never players). */
    public boolean attackNearest(String typeTag, double radius) {
        ResourceLocation id = ResourceLocation.tryParse(typeTag.startsWith("#") ? typeTag.substring(1) : typeTag);
        if (id == null) return false;
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, id);
        WorkerEntity w = ctx.worker();
        LivingEntity target = w.level().getEntitiesOfClass(LivingEntity.class, w.getBoundingBox().inflate(radius),
                        e -> e.isAlive() && !(e instanceof Player) && e != w && e.getType().is(tag))
                .stream().min(Comparator.comparingDouble(w::distanceToSqr)).orElse(null);
        if (target == null) return false;
        if (w.distanceTo(target) > 2.5) {
            ctx.moveTo(target, 2.0);
            return false;
        }
        return ctx.input().press(Action.ATTACK, WorkerInput.Target.entity(target));
    }

    // ---- supply ----
    /** Puts a stack into the supply container; returns what did not fit. */
    public ItemStack deposit(ItemStack stack) { return SupplyAccess.insert(ctx.worker(), stack); }
    /** Takes up to count matching items from the supply into the inventory. */
    public int takeFromSupply(Ingredient filter, int count) { return SupplyAccess.extract(ctx.worker(), filter, count); }

    static boolean has(WorkerEntity w, Ingredient i) {
        if (i.test(w.getMainHandItem())) return true;
        for (int s = 0; s < w.inventory().getContainerSize(); s++) if (i.test(w.inventory().getItem(s))) return true;
        return false;
    }

    private static Action action(String s) {
        return Action.valueOf(s.toUpperCase(Locale.ROOT));
    }
}
