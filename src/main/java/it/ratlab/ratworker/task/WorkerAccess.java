package it.ratlab.ratworker.task;

import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Who may command a worker. Always checked on the server. */
public final class WorkerAccess {
    private WorkerAccess() {}

    public static boolean canCommand(Player player, WorkerEntity worker) {
        return (ServerConfig.OP_BYPASS.get() && player.hasPermissions(2)) || isFriend(player, worker);
    }

    /** Owner or allowed by general.teamSharing, ignoring the op bypass. Also used for friendly fire. */
    public static boolean isFriend(Player player, WorkerEntity worker) {
        UUID owner = worker.getOwnerId();
        if (owner == null) return false;
        if (owner.equals(player.getUUID())) return true;
        return switch (ServerConfig.TEAM_SHARING.get()) {
            case EVERYONE -> true;
            case TEAM -> Compat.sameTeam(owner, player.getUUID());
            case OWNER -> false;
        };
    }
}
