package it.ratlab.ratworker.world;

import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/** Loaded workers on this server, plus the per-tick housekeeping that is not tied to one entity. */
public final class WorkerTracker {
    private static final Set<WorkerEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static int fakeInteractionsThisTick;

    private WorkerTracker() {}

    public static void onLoad(WorkerEntity w) {
        LOADED.add(w);
    }

    public static void onUnload(WorkerEntity w) {
        LOADED.remove(w);
    }

    public static int activeCount() {
        int n = 0;
        for (WorkerEntity w : LOADED) if (w.isAlive() && w.runner().isRunning()) n++;
        return n;
    }

    /** Budget for simulated inputs (phase 2). Returns false when this tick's budget is spent. */
    public static boolean tryUseInteraction(int max) {
        if (fakeInteractionsThisTick >= max) return false;
        fakeInteractionsThisTick++;
        return true;
    }

    public static void serverTick(MinecraftServer server) {
        fakeInteractionsThisTick = 0;
        if (server.getTickCount() % 20 != 0) return;
        WorkerSavedData data = WorkerSavedData.get(server);
        if (data.respawns.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<WorkerSavedData.Respawn> it = data.respawns.iterator(); it.hasNext(); ) {
            WorkerSavedData.Respawn r = it.next();
            if (r.readyAtGameTime() > now) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(r.owner());
            if (owner == null) continue; // wait for the owner, so the worker never appears in an unloaded spot
            ServerLevel level = owner.serverLevel();
            WorkerEntity w = ModEntities.WORKER.get().create(level);
            if (w == null) continue;
            w.load(r.entityData());
            w.prepareRespawn();
            w.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0);
            if (level.addFreshEntity(w)) {
                it.remove();
                data.setDirty();
                owner.sendSystemMessage(Component.translatable("ratworker.msg.respawned", w.getName()));
            }
        }
    }
}
