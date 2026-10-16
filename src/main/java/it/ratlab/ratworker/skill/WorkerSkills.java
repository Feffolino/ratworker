package it.ratlab.ratworker.skill;

import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.task.TaskRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One worker's training: spendable XP, tasks learned from tomes, and branch levels (nodes).
 * Innate tasks (skills.innateTasks) need no tome. KubeJS can change all of it (RatWorker.* bindings).
 */
public class WorkerSkills {
    private static final UUID HEALTH_MOD = UUID.fromString("5b0f8f3a-41c2-4d55-9b6e-1d0a5e7a1f01");
    private static final UUID STRENGTH_MOD = UUID.fromString("5b0f8f3a-41c2-4d55-9b6e-1d0a5e7a1f02");

    private double xp;
    private double pendingXp; // fractions from work, paid out in whole points
    private final Set<String> learned = new LinkedHashSet<>();
    private final Map<String, Integer> nodes = new HashMap<>();

    public double xp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = Math.max(0, xp);
    }

    public void addXp(double amount) {
        setXp(xp + amount);
    }

    /** Accrues work XP; returns whole points ready to be granted (after the KubeJS gainXp event). */
    public int accrue(double amount) {
        pendingXp += amount;
        int whole = (int) pendingXp;
        pendingXp -= whole;
        return whole;
    }

    public static boolean isInnate(WorkerTask task) {
        return ServerConfig.INNATE_TASKS.get().contains(task.id().toString()) || TaskRules.scriptInnate(task);
    }

    /** Client-safe guess for the creative tab (config may not be loaded): follow and stay. */
    public static boolean isInnateDefault(WorkerTask task) {
        String id = task.id().toString();
        return id.equals("ratworker:follow") || id.equals("ratworker:stay");
    }

    public boolean knows(WorkerTask task) {
        return isInnate(task) || learned.contains(task.id().toString());
    }

    public boolean knows(ResourceLocation id) {
        return learned.contains(id.toString());
    }

    public void learn(ResourceLocation id) {
        learned.add(id.toString());
    }

    public void forget(ResourceLocation id) {
        learned.remove(id.toString());
    }

    public Set<String> learned() {
        return learned;
    }

    public static int learnCost(WorkerTask task) {
        return (int) Math.round(TaskRules.number(task, TaskNumbers.LEARN_XP));
    }

    public int level(String nodeId) {
        return nodes.getOrDefault(nodeId, 0);
    }

    public int level(Branch b, @Nullable WorkerTask task) {
        if (!b.global && task == null) return 0;
        return level(b.nodeId(task == null ? "" : task.id().toString()));
    }

    public void setLevel(String nodeId, int level) {
        Branch b = branchOf(nodeId);
        int max = b == null ? level : b.maxLevel();
        int v = Math.max(0, Math.min(max, level));
        if (v == 0) nodes.remove(nodeId);
        else nodes.put(nodeId, v);
    }

    @Nullable
    public static Branch branchOf(String nodeId) {
        int i = nodeId.lastIndexOf('#');
        return Branch.fromKey(i < 0 ? nodeId : nodeId.substring(i + 1));
    }

    /** Spends XP on the next level. Returns null on success, else a lang key. */
    @Nullable
    public String upgrade(WorkerEntity w, String nodeId) {
        Branch b = branchOf(nodeId);
        if (b == null) return "ratworker.reason.unknown_node";
        int lvl = level(nodeId);
        if (lvl >= b.maxLevel()) return "ratworker.reason.node_max";
        int cost = b.cost(lvl);
        if (xp < cost) return "ratworker.reason.not_enough_xp";
        xp -= cost;
        setLevel(nodeId, lvl + 1);
        applyAttributes(w);
        return null;
    }

    /** Drain multiplier for a need while running {@code task}: 1 - perLevel x level, never below 0.1. */
    public double drainFactor(Branch need, @Nullable WorkerTask task) {
        if (task == null) return 1.0;
        return Math.max(0.1, 1.0 - need.perLevel() * level(need, task));
    }

    /** Max health and attack damage from the global branches. Call after load and after upgrades. */
    public void applyAttributes(WorkerEntity w) {
        set(w.getAttribute(Attributes.MAX_HEALTH), HEALTH_MOD, "ratworker health", level(Branch.HEALTH.key()) * Branch.HEALTH.perLevel());
        set(w.getAttribute(Attributes.ATTACK_DAMAGE), STRENGTH_MOD, "ratworker strength", level(Branch.STRENGTH.key()) * Branch.STRENGTH.perLevel());
        if (w.getHealth() > w.getMaxHealth()) w.setHealth(w.getMaxHealth());
    }

    private static void set(@Nullable AttributeInstance inst, UUID id, String name, double amount) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("Xp", xp);
        t.putDouble("PendingXp", pendingXp);
        ListTag l = new ListTag();
        learned.forEach(s -> l.add(StringTag.valueOf(s)));
        t.put("Learned", l);
        CompoundTag n = new CompoundTag();
        nodes.forEach(n::putInt);
        t.put("Nodes", n);
        return t;
    }

    public void load(CompoundTag t) {
        xp = t.getDouble("Xp");
        pendingXp = t.getDouble("PendingXp");
        learned.clear();
        for (Tag s : t.getList("Learned", Tag.TAG_STRING)) learned.add(s.getAsString());
        nodes.clear();
        CompoundTag n = t.getCompound("Nodes");
        for (String k : n.getAllKeys()) nodes.put(k, n.getInt(k));
    }

    public Map<String, Integer> nodes() {
        return nodes;
    }
}
