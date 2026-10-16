package it.ratlab.ratworker.supply;

import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.task.WorkerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Link supply" mode: after pressing SUPPLY in the command screen, the player's next right click on a
 * container (within 30 s) assigns it to that worker instead of opening it.
 */
public final class SupplyLinks {
    private static final int WINDOW_TICKS = 600;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(UUID worker, long expires) {}

    private SupplyLinks() {}

    public static void begin(ServerPlayer player, WorkerEntity worker) {
        PENDING.put(player.getUUID(), new Pending(worker.getUUID(), player.level().getGameTime() + WINDOW_TICKS));
        player.displayClientMessage(Component.translatable("ratworker.msg.link_supply", worker.getName()), false);
    }

    /** Validates and assigns. Returns a message key on failure, null on success. */
    public static String assign(ServerPlayer player, WorkerEntity worker, BlockPos pos) {
        if (!WorkerAccess.canCommand(player, worker)) return "ratworker.msg.not_yours";
        if (worker.level() != player.level() || !SupplyAccess.isContainer(worker.level(), pos)) return "ratworker.reason.not_container";
        int r = ServerConfig.SUPPLY_RANGE.get();
        if (worker.blockPosition().distSqr(pos) > (double) r * r) return "ratworker.reason.supply_far";
        worker.setSupply(pos);
        return null;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        Pending p = PENDING.get(player.getUUID());
        if (p == null) return;
        PENDING.remove(player.getUUID());
        if (player.level().getGameTime() > p.expires()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(((ServerLevel) player.level()).getEntity(p.worker()) instanceof WorkerEntity w)) {
            player.displayClientMessage(Component.translatable("ratworker.reason.worker_gone"), true);
            return;
        }
        String fail = assign(player, w, event.getPos());
        player.displayClientMessage(fail != null ? Component.translatable(fail)
                : Component.translatable("ratworker.msg.supply_set", w.getName(), event.getPos().toShortString()), true);
    }
}
