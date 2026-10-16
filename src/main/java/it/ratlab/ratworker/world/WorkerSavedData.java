package it.ratlab.ratworker.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide worker bookkeeping, stored with the overworld: every living worker (also unloaded ones,
 * so per-player limits hold), explicit unlocks from /ratworker unlock, and pending respawns.
 */
public class WorkerSavedData extends SavedData {
    private static final String NAME = "ratworker";

    public record Entry(UUID owner, String name, String dimension, BlockPos pos) {}

    public record Respawn(UUID owner, long readyAtGameTime, CompoundTag entityData) {}

    final Map<UUID, Entry> workers = new HashMap<>();
    final Map<UUID, Set<String>> unlocks = new HashMap<>();
    final List<Respawn> respawns = new ArrayList<>();

    public static WorkerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorkerSavedData::load, WorkerSavedData::new, NAME);
    }

    public int countOwnedBy(UUID owner) {
        int n = 0;
        for (Entry e : workers.values()) if (owner.equals(e.owner)) n++;
        for (Respawn r : respawns) if (owner.equals(r.owner)) n++;
        return n;
    }

    public Map<UUID, Entry> workers() {
        return workers;
    }

    public void put(UUID worker, Entry entry) {
        Entry old = workers.put(worker, entry);
        if (!entry.equals(old)) setDirty();
    }

    public void remove(UUID worker) {
        if (workers.remove(worker) != null) setDirty();
    }

    public boolean hasUnlock(UUID player, String key) {
        Set<String> s = unlocks.get(player);
        return s != null && s.contains(key);
    }

    public boolean setUnlock(UUID player, String key, boolean unlocked) {
        Set<String> s = unlocks.computeIfAbsent(player, k -> new HashSet<>());
        boolean changed = unlocked ? s.add(key) : s.remove(key);
        if (changed) setDirty();
        return changed;
    }

    public void addRespawn(Respawn r) {
        respawns.add(r);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        workers.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putUUID("Owner", e.owner);
            t.putString("Name", e.name);
            t.putString("Dim", e.dimension);
            t.putLong("Pos", e.pos.asLong());
            list.add(t);
        });
        tag.put("Workers", list);

        CompoundTag un = new CompoundTag();
        unlocks.forEach((id, set) -> {
            ListTag l = new ListTag();
            set.forEach(s -> l.add(StringTag.valueOf(s)));
            un.put(id.toString(), l);
        });
        tag.put("Unlocks", un);

        ListTag rs = new ListTag();
        for (Respawn r : respawns) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", r.owner);
            t.putLong("Ready", r.readyAtGameTime);
            t.put("Data", r.entityData);
            rs.add(t);
        }
        tag.put("Respawns", rs);
        return tag;
    }

    private static WorkerSavedData load(CompoundTag tag) {
        WorkerSavedData d = new WorkerSavedData();
        for (Tag t : tag.getList("Workers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.workers.put(c.getUUID("Id"), new Entry(c.getUUID("Owner"), c.getString("Name"), c.getString("Dim"), BlockPos.of(c.getLong("Pos"))));
        }
        CompoundTag un = tag.getCompound("Unlocks");
        for (String key : un.getAllKeys()) {
            Set<String> set = new HashSet<>();
            for (Tag s : un.getList(key, Tag.TAG_STRING)) set.add(s.getAsString());
            d.unlocks.put(UUID.fromString(key), set);
        }
        for (Tag t : tag.getList("Respawns", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.respawns.add(new Respawn(c.getUUID("Owner"), c.getLong("Ready"), c.getCompound("Data")));
        }
        return d;
    }
}
