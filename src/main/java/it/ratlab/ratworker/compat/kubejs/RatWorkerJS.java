package it.ratlab.ratworker.compat.kubejs;

import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.entity.Drinks;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.item.TomeItem;
import it.ratlab.ratworker.registry.ModItems;
import it.ratlab.ratworker.task.TaskRules;
import it.ratlab.ratworker.world.WorkerSavedData;
import it.ratlab.ratworker.world.WorkerSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The {@code RatWorker} global in server scripts. */
public class RatWorkerJS {
    public static final RatWorkerJS INSTANCE = new RatWorkerJS();
    /** {@code RatWorker.config.get('needs.hungerEnabled')}: read-only, resolved values for preset keys. */
    public final Config config = new Config();

    public static class Config {
        @Nullable
        public Object get(String key) {
            return switch (key) {
                case "general.maxWorkersPerPlayer" -> Settings.maxWorkersPerPlayer();
                case "general.maxWorkersPerChunk" -> Settings.maxWorkersPerChunk();
                case "general.maxActiveWorkersPerServer" -> Settings.maxActiveWorkers();
                case "needs.hungerEnabled" -> Settings.hungerEnabled();
                case "needs.thirstEnabled" -> Settings.thirstEnabled();
                case "needs.needsKill" -> Settings.needsKill();
                case "death.permaDeath" -> Settings.permaDeath();
                case "acquisition.requireStageToRecruit" -> Settings.requireStageToRecruit();
                case "unlocking.mode" -> Settings.unlockMode().name();
                default -> {
                    var cv = ServerConfig.value(key);
                    yield cv == null ? null : cv.get();
                }
            };
        }
    }

    /** Spawns a worker owned by a player (ignores per-player limits). Skin may be null for a random one. */
    @Nullable
    public WorkerEntity spawn(Level level, BlockPos pos, ServerPlayer owner, @Nullable String name, @Nullable String skin) {
        return spawn(level, pos, owner.getUUID(), owner.getGameProfile().getName(), name, skin, false);
    }

    @Nullable
    public WorkerEntity spawn(Level level, BlockPos pos, UUID owner, String ownerName, @Nullable String name, @Nullable String skin, boolean slim) {
        if (!(level instanceof ServerLevel sl)) return null;
        ResourceLocation s = skin == null || skin.isBlank() ? null : ResourceLocation.tryParse(skin);
        return WorkerSpawner.spawn(sl, Vec3.atBottomCenterOf(pos), owner, ownerName, name, s, s == null ? null : slim, true).worker();
    }

    public boolean isWorker(@Nullable Entity e) {
        return e instanceof WorkerEntity;
    }

    // ---- training ----
    public double getXp(WorkerEntity w) { return w.skills().xp(); }
    public void setXp(WorkerEntity w, double xp) { w.skills().setXp(xp); }
    public void addXp(WorkerEntity w, double xp) { w.skills().addXp(xp); }
    public boolean knows(WorkerEntity w, String task) { WorkerTask t = task(task); return t != null && w.skills().knows(t); }
    public void learn(WorkerEntity w, String task) { WorkerTask t = task(task); w.skills().learn(t != null ? t.id() : id(task)); }
    public void forget(WorkerEntity w, String task) { WorkerTask t = task(task); w.skills().forget(t != null ? t.id() : id(task)); }
    /** Node ids: 'health', 'strength', or '&lt;task&gt;#hunger|thirst|speed', e.g. 'ratworker:crank#speed'. */
    public int getNode(WorkerEntity w, String node) { return w.skills().level(node); }
    public void setNode(WorkerEntity w, String node, int level) { w.skills().setLevel(node, level); w.skills().applyAttributes(w); }
    /** ratworker:tome item that teaches the task. */
    public ItemStack tome(String task) { return TomeItem.of(ModItems.TOME.get(), id(task)); }
    /** Built-in manual with its own XP cost (0 = free). */
    public ItemStack tome(String task, int xp) { ItemStack s = tome(task); s.getOrCreateTag().putInt("Xp", xp); return s; }

    // ---- tasks ----
    /** Starts a task; returns an empty string on success or the reason. */
    public String startTask(WorkerEntity w, String task) {
        WorkerTask t = task(task);
        if (t == null) return "unknown task";
        TaskCheck c = w.startTask(t, null);
        return c.ok() ? "" : c.reason().getString();
    }

    public void stopTask(WorkerEntity w) { w.runner().stop(null); }

    public String currentTask(WorkerEntity w) { return w.runner().syncedId(); }

    /** Stage-mode unlock record, same as /ratworker unlock. */
    public void unlock(ServerPlayer player, String taskOrStage) {
        WorkerTask t = task(taskOrStage);
        WorkerSavedData.get(player.server).setUnlock(player.getUUID(), t != null ? TaskRules.stage(t) : taskOrStage, true);
    }

    // ---- needs ----
    public void setNeeds(WorkerEntity w, float hunger, float thirst) { w.needs().set(hunger, thirst); }
    /** Purity 0 dirty, 1 unsafe, 2 clean; -1 if not a drink. */
    public int purity(ItemStack stack) { Drinks.Info i = Drinks.of(stack); return i == null ? -1 : i.purity(); }
    public int drinkThirst(ItemStack stack) { Drinks.Info i = Drinks.of(stack); return i == null ? 0 : i.thirst(); }

    public void message(WorkerEntity w, String text) {
        var owner = w.getOwnerId() == null || w.getServer() == null ? null : w.getServer().getPlayerList().getPlayer(w.getOwnerId());
        if (owner != null) owner.sendSystemMessage(Component.literal(text));
    }

    private static ResourceLocation id(String s) {
        return s.contains(":") ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath("ratworker", s);
    }

    @Nullable
    private static WorkerTask task(String s) {
        WorkerTask t = WorkerTaskRegistry.INSTANCE.get(id(s));
        return t != null ? t : WorkerTaskRegistry.INSTANCE.get(ResourceLocation.fromNamespaceAndPath("kubejs", s));
    }
}
