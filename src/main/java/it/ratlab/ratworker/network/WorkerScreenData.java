package it.ratlab.ratworker.network;

import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.skill.Branch;
import it.ratlab.ratworker.task.TaskRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Everything the command screen shows. Built on the server; the client only renders it. */
public record WorkerScreenData(int entityId, Component name, int xp, List<NodeEntry> nodes, float health, float maxHealth,
                               float hunger, float thirst, boolean hungerOn, boolean thirstOn,
                               String currentTask, Component currentTitle, Component status, Component lastStop,
                               String owner, Component supply, boolean hasSupply, List<TaskEntry> tasks) {

    /** One upgrade node; task = "" for worker-wide branches. */
    public record NodeEntry(String id, String task, String branch, int level, int max, int cost) {}

    public record TaskEntry(String id, Component title, Component description, ItemStack icon, boolean available, Component reason) {}

    public static WorkerScreenData build(WorkerEntity w, ServerPlayer viewer) {
        List<TaskEntry> tasks = new ArrayList<>();
        for (WorkerTask t : WorkerTaskRegistry.INSTANCE.all()) {
            if (!TaskRules.isUnlocked(viewer, w, t)) continue; // locked tasks are hidden
            TaskCheck c = w.runner().check(t, viewer);
            tasks.add(new TaskEntry(t.id().toString(), t.title(), t.description(), t.icon(), c.ok(), c.reason()));
        }
        WorkerTask cur = w.runner().task();
        Component supply = w.supply() == null ? Component.translatable("ratworker.ui.none")
                : Component.literal(w.supply().toShortString());
        return new WorkerScreenData(w.getId(), w.getName(), (int) w.skills().xp(), nodes(w, tasks), w.getHealth(), w.getMaxHealth(),
                w.needs().hunger(), w.needs().thirst(), Settings.hungerEnabled(), Settings.thirstEnabled(),
                cur == null ? "" : cur.id().toString(), cur == null ? Component.translatable("ratworker.ui.idle") : cur.title(),
                w.runner().status(), w.runner().lastStop(), w.ownerName(), supply, w.supply() != null, tasks);
    }

    private static List<NodeEntry> nodes(WorkerEntity w, List<TaskEntry> tasks) {
        List<NodeEntry> out = new ArrayList<>();
        for (Branch b : Branch.values()) if (b.global) out.add(node(w, b, ""));
        for (TaskEntry te : tasks) {
            WorkerTask t = WorkerTaskRegistry.INSTANCE.get(ResourceLocation.parse(te.id()));
            if (t == null) continue;
            for (Branch b : t.branches()) if (!b.global) out.add(node(w, b, te.id()));
        }
        return out;
    }

    private static NodeEntry node(WorkerEntity w, Branch b, String task) {
        String id = b.nodeId(task);
        int lvl = w.skills().level(id);
        return new NodeEntry(id, task, b.key(), lvl, b.maxLevel(), lvl >= b.maxLevel() ? 0 : b.cost(lvl));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeComponent(name);
        buf.writeVarInt(xp);
        buf.writeVarInt(nodes.size());
        for (NodeEntry n : nodes) {
            buf.writeUtf(n.id);
            buf.writeUtf(n.task);
            buf.writeUtf(n.branch);
            buf.writeVarInt(n.level);
            buf.writeVarInt(n.max);
            buf.writeVarInt(n.cost);
        }
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeFloat(hunger);
        buf.writeFloat(thirst);
        buf.writeBoolean(hungerOn);
        buf.writeBoolean(thirstOn);
        buf.writeUtf(currentTask);
        buf.writeComponent(currentTitle);
        buf.writeComponent(status);
        buf.writeComponent(lastStop);
        buf.writeUtf(owner);
        buf.writeComponent(supply);
        buf.writeBoolean(hasSupply);
        buf.writeVarInt(tasks.size());
        for (TaskEntry t : tasks) {
            buf.writeUtf(t.id);
            buf.writeComponent(t.title);
            buf.writeComponent(t.description);
            buf.writeItem(t.icon);
            buf.writeBoolean(t.available);
            buf.writeComponent(t.reason);
        }
    }

    public static WorkerScreenData read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Component name = buf.readComponent();
        int xp = buf.readVarInt();
        int nn = buf.readVarInt();
        List<NodeEntry> nodes = new ArrayList<>(nn);
        for (int i = 0; i < nn; i++) nodes.add(new NodeEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        float hp = buf.readFloat(), max = buf.readFloat(), hunger = buf.readFloat(), thirst = buf.readFloat();
        boolean hOn = buf.readBoolean(), tOn = buf.readBoolean();
        String cur = buf.readUtf();
        Component curTitle = buf.readComponent(), status = buf.readComponent(), lastStop = buf.readComponent();
        String owner = buf.readUtf();
        Component supply = buf.readComponent();
        boolean hasSupply = buf.readBoolean();
        int n = buf.readVarInt();
        List<TaskEntry> tasks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tasks.add(new TaskEntry(buf.readUtf(), buf.readComponent(), buf.readComponent(), buf.readItem(), buf.readBoolean(), buf.readComponent()));
        }
        return new WorkerScreenData(id, name, xp, nodes, hp, max, hunger, thirst, hOn, tOn, cur, curTitle, status, lastStop, owner, supply, hasSupply, tasks);
    }
}
