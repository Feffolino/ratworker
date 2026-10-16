package it.ratlab.ratworker.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.task.TaskRules;
import it.ratlab.ratworker.task.WorkerAccess;
import it.ratlab.ratworker.world.WorkerSavedData;
import it.ratlab.ratworker.world.WorkerSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import it.ratlab.ratworker.supply.SupplyAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** /ratworker ... All rules are enforced here too; nothing trusts the client. */
public final class RatWorkerCommand {
    private RatWorkerCommand() {}

    private static final SuggestionProvider<CommandSourceStack> TASKS = (c, b) ->
            SharedSuggestionProvider.suggestResource(WorkerTaskRegistry.INSTANCE.all().stream().map(WorkerTask::id), b);
    private static final SuggestionProvider<CommandSourceStack> KEYS = (c, b) ->
            SharedSuggestionProvider.suggest(ServerConfig.keys(), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ratworker");

        root.then(Commands.literal("task")
                .then(Commands.argument("workers", EntityArgument.entities())
                        .then(Commands.argument("task", ResourceLocationArgument.id()).suggests(TASKS)
                                .executes(RatWorkerCommand::task))));
        root.then(Commands.literal("stop")
                .then(Commands.argument("workers", EntityArgument.entities()).executes(RatWorkerCommand::stop)));
        root.then(Commands.literal("needs")
                .then(Commands.literal("get").then(Commands.argument("workers", EntityArgument.entities()).executes(RatWorkerCommand::needsGet)))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("workers", EntityArgument.entities())
                                .then(Commands.argument("hunger", FloatArgumentType.floatArg(0, 20))
                                        .then(Commands.argument("thirst", FloatArgumentType.floatArg(0, 20)).executes(RatWorkerCommand::needsSet))))));
        root.then(Commands.literal("unlock").requires(s -> s.hasPermission(2))
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("task", StringArgumentType.greedyString()).suggests(TASKS)
                                .executes(c -> unlock(c, true)))));
        root.then(Commands.literal("lock").requires(s -> s.hasPermission(2))
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("task", StringArgumentType.greedyString()).suggests(TASKS)
                                .executes(c -> unlock(c, false)))));
        root.then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
                .executes(c -> spawn(c, null))
                .then(Commands.argument("name", StringArgumentType.word()).executes(c -> spawn(c, StringArgumentType.getString(c, "name")))));
        root.then(Commands.literal("skills").requires(s -> s.hasPermission(2))
                .then(Commands.argument("workers", EntityArgument.entities())
                        .then(Commands.literal("get").executes(RatWorkerCommand::skillsGet))
                        .then(Commands.literal("xp").then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                .executes(c -> skillsEach(c, w -> w.skills().addXp(DoubleArgumentType.getDouble(c, "amount"))))))
                        .then(Commands.literal("learn").then(Commands.argument("task", ResourceLocationArgument.id()).suggests(TASKS)
                                .executes(c -> skillsEach(c, w -> w.skills().learn(taskId(c))))))
                        .then(Commands.literal("forget").then(Commands.argument("task", ResourceLocationArgument.id()).suggests(TASKS)
                                .executes(c -> skillsEach(c, w -> w.skills().forget(taskId(c))))))
                        .then(Commands.literal("node").then(Commands.argument("node", StringArgumentType.string())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> skillsEach(c, w -> {
                                            w.skills().setLevel(StringArgumentType.getString(c, "node"), IntegerArgumentType.getInteger(c, "level"));
                                            w.skills().applyAttributes(w);
                                        })))))));
        root.then(Commands.literal("supply")
                .then(Commands.argument("workers", EntityArgument.entities())
                        .then(Commands.literal("clear").executes(c -> supply(c, null)))
                        .then(Commands.literal("set").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> supply(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))))));
        root.then(Commands.literal("list").executes(RatWorkerCommand::list));
        root.then(Commands.literal("config").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reload").executes(RatWorkerCommand::configReload))
                .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.string()).suggests(KEYS).executes(RatWorkerCommand::configGet)))
                .then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.string()).suggests(KEYS)
                        .then(Commands.argument("value", StringArgumentType.greedyString()).executes(RatWorkerCommand::configSet)))));
        d.register(root);
    }

    /** Workers from the selector the source may command (ops: all, others: own/team if allowCommands). */
    private static List<WorkerEntity> workers(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<? extends Entity> found = EntityArgument.getEntities(c, "workers");
        CommandSourceStack src = c.getSource();
        boolean op = src.hasPermission(2);
        ServerPlayer player = src.getPlayer();
        List<WorkerEntity> out = new ArrayList<>();
        for (Entity e : found) {
            if (!(e instanceof WorkerEntity w)) continue;
            if (op || (ServerConfig.ALLOW_COMMANDS.get() && player != null && WorkerAccess.canCommand(player, w))) out.add(w);
        }
        if (out.isEmpty()) src.sendFailure(Component.translatable("ratworker.cmd.no_workers"));
        return out;
    }

    private static int task(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(c, "task");
        WorkerTask task = WorkerTaskRegistry.INSTANCE.get(id);
        // a bare id parses as minecraft:<id>; map it to ratworker:<id>
        if (task == null && id.getNamespace().equals("minecraft")) task = resolveTask(id.getPath());
        if (task == null) {
            c.getSource().sendFailure(Component.translatable("ratworker.reason.unknown_task", id.toString()));
            return 0;
        }
        ServerPlayer player = c.getSource().getPlayer();
        boolean op = c.getSource().hasPermission(2);
        int n = 0;
        for (WorkerEntity w : workers(c)) {
            if (!op && player != null && !TaskRules.isUnlocked(player, w, task)) {
                c.getSource().sendFailure(Component.translatable("ratworker.cmd.locked", task.title()));
                continue;
            }
            TaskCheck r = w.startTask(task, player);
            if (r.ok()) n++;
            else c.getSource().sendFailure(Component.translatable("ratworker.cmd.cannot_start", w.getName(), r.reason()));
        }
        int count = n;
        Component title = task.title();
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.started", title, count), false);
        return n;
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<WorkerEntity> ws = workers(c);
        for (WorkerEntity w : ws) w.runner().stop(Component.translatable("ratworker.reason.stopped_by", c.getSource().getDisplayName()));
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.stopped", ws.size()), false);
        return ws.size();
    }

    private static int needsGet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<WorkerEntity> ws = workers(c);
        for (WorkerEntity w : ws) {
            c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.needs", w.getName(),
                    String.format("%.1f", w.needs().hunger()), String.format("%.1f", w.needs().thirst())), false);
        }
        return ws.size();
    }

    private static int needsSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        float h = FloatArgumentType.getFloat(c, "hunger"), t = FloatArgumentType.getFloat(c, "thirst");
        List<WorkerEntity> ws = workers(c);
        for (WorkerEntity w : ws) w.needs().set(h, t);
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.needs_set", ws.size()), true);
        return ws.size();
    }

    /** Accepts a task id (stores its stage name) or any raw stage name, e.g. ratworker_recruit. */
    private static int unlock(CommandContext<CommandSourceStack> c, boolean unlock) throws CommandSyntaxException {
        String arg = StringArgumentType.getString(c, "task").trim();
        WorkerTask task = resolveTask(arg);
        String stage = task != null ? TaskRules.stage(task) : arg;
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        WorkerSavedData data = WorkerSavedData.get(c.getSource().getServer());
        for (ServerPlayer p : players) data.setUnlock(p.getUUID(), stage, unlock);
        c.getSource().sendSuccess(() -> Component.translatable(unlock ? "ratworker.cmd.unlocked" : "ratworker.cmd.locked_stage", stage, players.size()), true);
        return players.size();
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorkerSpawner.Result r = WorkerSpawner.spawn(c.getSource().getLevel(), c.getSource().getPosition(), p.getUUID(),
                p.getGameProfile().getName(), name, null, null, false);
        if (!r.ok()) {
            c.getSource().sendFailure(r.reason());
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.msg.recruited", r.worker().getName()), true);
        return 1;
    }

    private static ResourceLocation taskId(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "task");
        if (WorkerTaskRegistry.INSTANCE.get(id) == null && id.getNamespace().equals("minecraft")) {
            WorkerTask t = resolveTask(id.getPath());
            if (t != null) return t.id();
        }
        return id;
    }

    private static int skillsEach(CommandContext<CommandSourceStack> c, java.util.function.Consumer<WorkerEntity> action) throws CommandSyntaxException {
        List<WorkerEntity> ws = workers(c);
        for (WorkerEntity w : ws) action.accept(w);
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.skills_set", ws.size()), true);
        return ws.size();
    }

    private static int skillsGet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<WorkerEntity> ws = workers(c);
        for (WorkerEntity w : ws) {
            String text = w.getName().getString() + ": xp " + (int) w.skills().xp() + ", learned " + w.skills().learned() + ", nodes " + w.skills().nodes();
            c.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        return ws.size();
    }

    private static int supply(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
        List<WorkerEntity> ws = workers(c);
        int n = 0;
        for (WorkerEntity w : ws) {
            if (pos == null) {
                w.setSupply(null);
                n++;
                continue;
            }
            if (!SupplyAccess.isContainer(w.level(), pos)) {
                c.getSource().sendFailure(Component.translatable("ratworker.reason.not_container"));
                return 0;
            }
            int r = ServerConfig.SUPPLY_RANGE.get();
            if (w.blockPosition().distSqr(pos) > (double) r * r) {
                c.getSource().sendFailure(Component.translatable("ratworker.cmd.cannot_start", w.getName(), Component.translatable("ratworker.reason.supply_far")));
                continue;
            }
            w.setSupply(pos);
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.supply", count), false);
        return n;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        UUID id = p.getUUID();
        int n = 0;
        for (var e : WorkerSavedData.get(c.getSource().getServer()).workers().entrySet()) {
            if (!id.equals(e.getValue().owner())) continue;
            n++;
            var en = e.getValue();
            c.getSource().sendSuccess(() -> Component.literal(" - " + en.name() + "  " + en.dimension() + " " + en.pos().toShortString()), false);
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("ratworker.cmd.list", count, Settings.maxWorkersPerPlayer()), false);
        return n;
    }

    private static int configReload(CommandContext<CommandSourceStack> c) {
        boolean ok = ServerConfig.reloadFromDisk();
        c.getSource().sendSuccess(() -> Component.translatable(ok ? "ratworker.cmd.config_reloaded" : "ratworker.cmd.config_not_file"), true);
        return ok ? 1 : 0;
    }

    private static int configGet(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "key");
        String v = Settings.describe(key);
        if (v == null) {
            c.getSource().sendFailure(Component.translatable("ratworker.cmd.config_unknown", key));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(key + " = " + v + (ServerConfig.RESTART_KEYS.contains(key) ? "  [restart]" : "  [live]")), false);
        return 1;
    }

    private static int configSet(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "key");
        String value = StringArgumentType.getString(c, "value");
        try {
            ServerConfig.set(key, value);
        } catch (IllegalArgumentException ex) {
            c.getSource().sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
        boolean restart = ServerConfig.RESTART_KEYS.contains(key);
        c.getSource().sendSuccess(() -> Component.translatable(restart ? "ratworker.cmd.config_set_restart" : "ratworker.cmd.config_set", key, Settings.describe(key)), true);
        return 1;
    }

    /** "follow" means ratworker:follow; namespaced ids are used as given. */
    static WorkerTask resolveTask(String raw) {
        ResourceLocation id = raw.contains(":") ? ResourceLocation.tryParse(raw) : ResourceLocation.tryParse("ratworker:" + raw);
        return id == null ? null : WorkerTaskRegistry.INSTANCE.get(id);
    }
}
