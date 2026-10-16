package it.ratlab.ratworker.world;

import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.data.WorkerDataLoader;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The one way workers are created: contract, command and scripts all go through here. */
public final class WorkerSpawner {
    private WorkerSpawner() {}

    public record Result(@Nullable WorkerEntity worker, Component reason) {
        public boolean ok() {
            return worker != null;
        }
    }

    public static Result spawn(ServerLevel level, Vec3 pos, UUID owner, String ownerName, @Nullable String name,
                               @Nullable ResourceLocation skin, @Nullable Boolean slim, boolean ignoreLimits) {
        if (!Settings.enabled()) return fail("ratworker.reason.disabled");
        if (!ignoreLimits) {
            int max = Settings.maxWorkersPerPlayer();
            if (WorkerSavedData.get(level.getServer()).countOwnedBy(owner) >= max) return fail("ratworker.reason.player_limit", max);
            int maxChunk = Settings.maxWorkersPerChunk();
            ChunkPos cp = new ChunkPos(net.minecraft.core.BlockPos.containing(pos));
            AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                    cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
            if (level.getEntitiesOfClass(WorkerEntity.class, box).size() >= maxChunk) return fail("ratworker.reason.chunk_limit", maxChunk);
        }
        WorkerEntity w = ModEntities.WORKER.get().create(level);
        if (w == null) return fail("ratworker.reason.spawn_failed");
        RandomSource rnd = level.getRandom();
        w.moveTo(pos.x, pos.y, pos.z, rnd.nextFloat() * 360f, 0);
        w.setOwner(owner, ownerName);
        w.setCustomName(Component.literal(name != null && !name.isBlank() ? name : randomName(rnd)));
        WorkerDataLoader.Skin s = skin != null ? new WorkerDataLoader.Skin(skin, slim != null && slim) : randomSkin(rnd);
        w.setSkin(s.texture(), slim != null ? slim : s.slim());
        w.finalizeSpawn(level, level.getCurrentDifficultyAt(w.blockPosition()), MobSpawnType.COMMAND, null, null);
        if (!level.addFreshEntity(w)) return fail("ratworker.reason.spawn_failed");
        return new Result(w, Component.empty());
    }

    private static Result fail(String key, Object... args) {
        return new Result(null, Component.translatable(key, args));
    }

    public static List<String> namePool() {
        List<? extends String> cfg = ServerConfig.NAME_POOL.get();
        if (!cfg.isEmpty()) return new ArrayList<>(cfg);
        return WorkerDataLoader.INSTANCE.data().names();
    }

    public static List<WorkerDataLoader.Skin> skinPool() {
        List<? extends String> cfg = ServerConfig.SKIN_POOL.get();
        List<WorkerDataLoader.Skin> out = new ArrayList<>();
        for (String entry : cfg) {
            String[] parts = entry.split(";");
            ResourceLocation rl = ResourceLocation.tryParse(parts[0].trim());
            if (rl != null) out.add(new WorkerDataLoader.Skin(rl, parts.length > 1 && parts[1].trim().equalsIgnoreCase("slim")));
        }
        return out.isEmpty() ? WorkerDataLoader.INSTANCE.data().skins() : out;
    }

    public static String randomName(RandomSource rnd) {
        List<String> pool = namePool();
        return pool.isEmpty() ? "Worker" : pool.get(rnd.nextInt(pool.size()));
    }

    public static WorkerDataLoader.Skin randomSkin(RandomSource rnd) {
        List<WorkerDataLoader.Skin> pool = skinPool();
        return pool.isEmpty() ? new WorkerDataLoader.Skin(WorkerEntity.DEFAULT_SKIN, false) : pool.get(rnd.nextInt(pool.size()));
    }
}
