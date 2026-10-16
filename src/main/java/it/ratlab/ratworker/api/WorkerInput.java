package it.ratlab.ratworker.api;

import com.mojang.authlib.GameProfile;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.world.WorkerTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simulated player input for one worker: press, hold and release of use (right click), attack (left click)
 * and sneak. Interactions go through a Forge {@link FakePlayer} that carries the owner's UUID, through
 * {@code ServerPlayerGameMode}, so block protection (claims) and Forge interaction events apply as for a real
 * player. A held use is re-sent every {@code performance.holdIntervalTicks}, which is what a client does while
 * the right button is held (a Create hand crank keeps spinning only while it keeps receiving use).
 * <p>
 * Safety: targets must be within reach, never a player, and never a container other than the assigned supply.
 * Every hold is released when the task stops, the worker dies or unloads, needs hit zero, or the target is gone.
 */
public class WorkerInput {
    public static final double REACH = 4.5;
    private static final String FAKE_NAME = "[RatWorker]";

    public enum Action { USE, ATTACK, SNEAK }

    /** A block, an entity, or (both null) whatever the worker looks at. */
    public record Target(@Nullable BlockPos pos, @Nullable Entity entity) {
        public static Target block(BlockPos pos) {
            return new Target(pos.immutable(), null);
        }

        public static Target entity(Entity e) {
            return new Target(null, e);
        }

        public static Target inFront() {
            return new Target(null, null);
        }
    }

    private static final class Held {
        final Target target;
        final int interval; // ticks between repeats, <= 0 = performance.holdIntervalTicks
        long lastFire = Long.MIN_VALUE / 2;
        float breakProgress;

        Held(Target target) {
            this(target, 0);
        }

        Held(Target target, int interval) {
            this.target = target;
            this.interval = interval;
        }
    }

    private final WorkerEntity worker;
    private final Map<Action, Held> held = new EnumMap<>(Action.class);

    public WorkerInput(WorkerEntity worker) {
        this.worker = worker;
    }

    /** Starts holding. Returns false if the target is not allowed. */
    public boolean hold(Action action, Target target) {
        return hold(action, target, 0);
    }

    /** Starts holding, repeating every {@code intervalTicks} (slower clicks = less work, e.g. a crank with gaps). */
    public boolean hold(Action action, Target target, int intervalTicks) {
        if (action == Action.SNEAK) {
            held.put(action, new Held(target));
            worker.setShiftKeyDown(true);
            return true;
        }
        Target resolved = resolve(target);
        if (resolved == null || !allowed(action, resolved)) return false;
        Held cur = held.get(action);
        if (cur == null || !sameTarget(cur.target, resolved) || cur.interval != intervalTicks) held.put(action, new Held(resolved, intervalTicks));
        return true;
    }

    /** One tap. Returns false if not allowed or the server interaction budget is spent this tick. */
    public boolean press(Action action, Target target) {
        if (action == Action.SNEAK) return true;
        Target resolved = resolve(target);
        if (resolved == null || !allowed(action, resolved)) return false;
        if (!WorkerTracker.tryUseInteraction(ServerConfig.MAX_FAKE_INTERACTIONS.get())) return false;
        Held tap = new Held(resolved);
        if (action == Action.ATTACK && resolved.pos() != null) tap.breakProgress = 0;
        fire(action, tap, true);
        return true;
    }

    /** Right click on a block with the stack of a worker inventory slot (seeds, hoe, bucket). Face given. */
    public boolean useSlot(int slot, BlockPos pos, Direction face) {
        Target t = Target.block(pos);
        if (!allowed(Action.USE, t) || !WorkerTracker.tryUseInteraction(ServerConfig.MAX_FAKE_INTERACTIONS.get())) return false;
        FakePlayer fp = fake();
        fp.getFoodData().setExhaustion(0);
        ItemStack stack = worker.inventory().getItem(slot);
        fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            Vec3 c = Vec3.atCenterOf(pos);
            aim(fp, c);
            Vec3 hitVec = c.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            fp.gameMode.useItemOn(fp, worker.level(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, face, pos, false));
            worker.swing(InteractionHand.MAIN_HAND);
            return true;
        } finally {
            worker.needs().addExhaustion(fp.getFoodData().getExhaustionLevel() * ServerConfig.ACTION_EXHAUSTION.get().floatValue());
            fp.getFoodData().setExhaustion(0);
            worker.inventory().setItem(slot, fp.getMainHandItem());
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Breaks a block now with the main-hand tool, as a player would (drops, BreakEvent, claims). */
    public boolean breakNow(BlockPos pos) {
        Target t = Target.block(pos);
        if (!allowed(Action.ATTACK, t) || !WorkerTracker.tryUseInteraction(ServerConfig.MAX_FAKE_INTERACTIONS.get())) return false;
        FakePlayer fp = fake();
        fp.getFoodData().setExhaustion(0);
        fp.setItemInHand(InteractionHand.MAIN_HAND, worker.getMainHandItem());
        try {
            aim(fp, Vec3.atCenterOf(pos));
            worker.swing(InteractionHand.MAIN_HAND);
            return fp.gameMode.destroyBlock(pos);
        } finally {
            worker.needs().addExhaustion(fp.getFoodData().getExhaustionLevel() * ServerConfig.ACTION_EXHAUSTION.get().floatValue());
            fp.getFoodData().setExhaustion(0);
            worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, fp.getMainHandItem());
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    public void release(Action action) {
        Held h = held.remove(action);
        if (h == null) return;
        if (action == Action.SNEAK) worker.setShiftKeyDown(false);
        if (action == Action.ATTACK && h.target.pos() != null) worker.level().destroyBlockProgress(worker.getId(), h.target.pos(), -1);
    }

    public void releaseAll() {
        for (Action a : Action.values()) release(a);
    }

    public boolean isHeld(Action action) {
        return held.containsKey(action);
    }

    /** Server tick, called by the worker every tick. */
    public void tick() {
        if (held.isEmpty()) return;
        if (!worker.isAlive() || worker.needs().anyEmpty()) {
            releaseAll();
            return;
        }
        long now = worker.level().getGameTime();
        int interval = ServerConfig.HOLD_INTERVAL.get();
        for (Action a : Action.values()) {
            Held h = held.get(a);
            if (h == null || a == Action.SNEAK) continue;
            if (!stillValid(h.target)) {
                release(a);
                continue;
            }
            if (!inReach(h.target)) continue; // keep the hold, the task is walking back
            // attack on a block mines continuously; everything else repeats at the hold cadence
            boolean mining = a == Action.ATTACK && h.target.pos() != null;
            if (!mining && now - h.lastFire < (h.interval > 0 ? h.interval : interval)) continue;
            if (!WorkerTracker.tryUseInteraction(ServerConfig.MAX_FAKE_INTERACTIONS.get())) continue;
            h.lastFire = now;
            fire(a, h, false);
        }
    }

    // ---- internals ----

    @Nullable
    private Target resolve(Target t) {
        if (t.pos() != null || t.entity() != null) return t;
        Vec3 eye = worker.getEyePosition();
        Vec3 end = eye.add(worker.getViewVector(1f).scale(REACH));
        BlockHitResult hit = worker.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, worker));
        return hit.getType() == HitResult.Type.BLOCK ? Target.block(hit.getBlockPos()) : null;
    }

    private static boolean sameTarget(Target a, Target b) {
        if (a.pos() != null) return a.pos().equals(b.pos());
        return a.entity() != null && a.entity() == b.entity();
    }

    private boolean allowed(Action action, Target t) {
        if (!stillValid(t) || !inReach(t)) return false;
        if (t.entity() != null) return !(t.entity() instanceof Player) && t.entity() != worker;
        if (action == Action.USE) {
            BlockEntity be = worker.level().getBlockEntity(t.pos());
            boolean container = be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
            return !container || t.pos().equals(worker.supply());
        }
        return true;
    }

    private boolean stillValid(Target t) {
        if (t.entity() != null) return t.entity().isAlive() && t.entity().level() == worker.level();
        return t.pos() != null && worker.level().isLoaded(t.pos()) && !worker.level().getBlockState(t.pos()).isAir();
    }

    private boolean inReach(Target t) {
        Vec3 point = t.entity() != null ? t.entity().position().add(0, t.entity().getBbHeight() / 2, 0) : Vec3.atCenterOf(t.pos());
        return worker.getEyePosition().distanceTo(point) <= REACH;
    }

    /**
     * The worker's fake player (owner UUID), standing on the worker, with no leftover exhaustion.
     * For item logic that only works with a player (e.g. modded drinks). Empty its hands/inventory after use.
     */
    public FakePlayer fakePlayer() {
        FakePlayer fp = fake();
        fp.getFoodData().setExhaustion(0);
        return fp;
    }

    private FakePlayer fake() {
        ServerLevel level = (ServerLevel) worker.level();
        UUID id = worker.getOwnerId() != null ? worker.getOwnerId() : worker.getUUID();
        FakePlayer fp = FakePlayerFactory.get(level, new GameProfile(id, FAKE_NAME));
        fp.setPos(worker.getX(), worker.getY(), worker.getZ());
        fp.setShiftKeyDown(isHeld(Action.SNEAK));
        return fp;
    }

    private void aim(FakePlayer fp, Vec3 at) {
        worker.getLookControl().setLookAt(at.x, at.y, at.z);
        Vec3 d = at.subtract(fp.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        fp.setYRot(yaw);
        fp.setXRot(pitch);
        fp.setYHeadRot(yaw);
    }

    private void fire(Action action, Held h, boolean tap) {
        FakePlayer fp = fake();
        fp.getFoodData().setExhaustion(0);
        ItemStack hand = worker.getMainHandItem();
        fp.setItemInHand(InteractionHand.MAIN_HAND, hand);
        try {
            if (h.target.entity() != null) {
                Entity e = h.target.entity();
                aim(fp, e.position().add(0, e.getBbHeight() / 2, 0));
                if (action == Action.USE) fp.interactOn(e, InteractionHand.MAIN_HAND);
                else if (worker.doHurtTarget(e)) worker.needs().addExhaustion(0.1f); // worker's own attack, player cost
            } else {
                BlockPos pos = h.target.pos();
                Vec3 center = Vec3.atCenterOf(pos);
                aim(fp, center);
                Direction face = Direction.getNearest(worker.getEyePosition().x - center.x,
                        worker.getEyePosition().y - center.y, worker.getEyePosition().z - center.z);
                if (action == Action.USE) {
                    Vec3 hitVec = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                    fp.gameMode.useItemOn(fp, worker.level(), hand, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, face, pos, false));
                } else {
                    mine(fp, h, pos, tap);
                }
            }
            worker.swing(InteractionHand.MAIN_HAND);
        } finally {
            // effort the block/item charged to the "player" (Create crank, mining) becomes the worker's
            float spent = fp.getFoodData().getExhaustionLevel();
            fp.getFoodData().setExhaustion(0);
            worker.needs().addExhaustion(spent * ServerConfig.ACTION_EXHAUSTION.get().floatValue());
            // the stack may have been used up or replaced (bucket filled, tool broken)
            worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, fp.getMainHandItem());
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private void mine(FakePlayer fp, Held h, BlockPos pos, boolean tap) {
        BlockState state = worker.level().getBlockState(pos);
        float step = state.getDestroyProgress(fp, worker.level(), pos);
        h.breakProgress += tap ? Math.max(step, 0) : step;
        if (h.breakProgress >= 1f || (tap && step >= 1f)) {
            fp.gameMode.destroyBlock(pos); // fires BlockEvent.BreakEvent, so claims apply
            worker.level().destroyBlockProgress(worker.getId(), pos, -1);
            h.breakProgress = 0;
        } else {
            worker.level().destroyBlockProgress(worker.getId(), pos, (int) (h.breakProgress * 10));
        }
    }
}
