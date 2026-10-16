package it.ratlab.ratworker.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import it.ratlab.ratworker.config.Preset.NameMode;
import it.ratlab.ratworker.config.Preset.TeamSharing;
import it.ratlab.ratworker.config.Preset.ThreatBehaviour;
import it.ratlab.ratworker.config.Preset.TriState;
import it.ratlab.ratworker.config.Preset.UnlockMode;
import it.ratlab.ratworker.skill.Branch;
import it.ratlab.ratworker.task.builtin.BuiltinTasks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-world server config (serverconfig/ratworker-server.toml, seeded from defaultconfigs/).
 * Forge syncs it to clients on login. Read resolved values through {@link Settings}, not directly.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    // general
    public static final EnumValue<Preset> PRESET;
    public static final BooleanValue ENABLED;
    public static final IntValue MAX_PER_PLAYER;
    public static final IntValue MAX_PER_CHUNK;
    public static final IntValue MAX_ACTIVE;
    public static final BooleanValue WORK_OFFLINE;
    public static final BooleanValue OP_BYPASS;
    public static final EnumValue<TeamSharing> TEAM_SHARING;
    public static final BooleanValue ALLOW_COMMANDS;
    // acquisition
    public static final BooleanValue CONTRACT_ENABLED;
    public static final BooleanValue CONTRACT_REQUIRES_SPOT;
    public static final EnumValue<TriState> REQUIRE_STAGE_TO_RECRUIT;
    public static final ConfigValue<String> RECRUIT_STAGE;
    public static final EnumValue<NameMode> NAME_MODE;
    public static final ConfigValue<List<? extends String>> NAME_POOL;
    public static final ConfigValue<List<? extends String>> SKIN_POOL;
    // needs
    public static final EnumValue<TriState> HUNGER_ENABLED;
    public static final EnumValue<TriState> THIRST_ENABLED;
    public static final DoubleValue HUNGER_IDLE;
    public static final DoubleValue HUNGER_WORKING;
    public static final DoubleValue THIRST_IDLE;
    public static final DoubleValue THIRST_WORKING;
    public static final IntValue EAT_BELOW;
    public static final IntValue DRINK_BELOW;
    public static final IntValue LOW_THRESHOLD;
    public static final DoubleValue LOW_SLOWDOWN;
    public static final BooleanValue PURITY_EFFECTS;
    public static final ConfigValue<String> DIRTY_EFFECT;
    public static final IntValue DIRTY_SECONDS;
    public static final ConfigValue<String> UNSAFE_EFFECT;
    public static final IntValue UNSAFE_SECONDS;
    public static final IntValue SUPPLY_RANGE;
    public static final IntValue REGEN_ABOVE;
    public static final IntValue REGEN_INTERVAL;
    public static final DoubleValue REGEN_EXHAUSTION;
    public static final DoubleValue DRAIN_MULTIPLIER;
    public static final DoubleValue ACTION_EXHAUSTION;
    public static final DoubleValue THIRST_EXHAUSTION_RATIO;
    // skills
    public static final BooleanValue XP_FROM_WORK;
    public static final DoubleValue XP_PER_MINUTE;
    public static final ConfigValue<List<? extends String>> INNATE_TASKS;
    public static final Map<Branch, BranchSection> BRANCHES = new EnumMap<>(Branch.class);

    public record BranchSection(IntValue maxLevel, DoubleValue baseCost, DoubleValue costGrowth, DoubleValue perLevel) {}
    public static final IntValue SUPPLY_TAKE;
    public static final DoubleValue STARVATION_DAMAGE;
    public static final IntValue STARVATION_INTERVAL;
    public static final EnumValue<TriState> NEEDS_KILL;
    public static final ConfigValue<String> FOOD_TAG;
    public static final ConfigValue<String> DRINK_TAG;
    // death
    public static final EnumValue<TriState> PERMA_DEATH;
    public static final BooleanValue DROP_INVENTORY;
    public static final BooleanValue OWNER_FRIENDLY_FIRE;
    public static final BooleanValue TEAM_FRIENDLY_FIRE;
    public static final IntValue RESPAWN_COOLDOWN;
    // safety
    public static final BooleanValue RADIATION;
    public static final BooleanValue HAZMAT_PROTECTS;
    public static final DoubleValue FLEE_HEALTH;
    public static final EnumValue<ThreatBehaviour> THREAT_BEHAVIOUR;
    public static final ConfigValue<String> HOSTILE_TAG;
    // performance
    public static final IntValue MAX_BLOCK_SCANS;
    public static final IntValue PATH_RECOMPUTE;
    public static final IntValue TASK_TICK_INTERVAL;
    public static final IntValue STUCK_TICKS;
    public static final IntValue MAX_PATH_FAILURES;
    public static final IntValue HOLD_INTERVAL;
    public static final IntValue MAX_FAKE_INTERACTIONS;
    // unlocking
    public static final EnumValue<UnlockMode> UNLOCK_MODE;
    public static final ConfigValue<String> STAGE_TEMPLATE;
    public static final ConfigValue<String> LEVEL_STAGE_TEMPLATE;
    public static final BooleanValue SCOREBOARD_TAGS;
    // leveling (data model ready, off by default)
    public static final BooleanValue LEVELING;

    /** Built-in task sections: task path -> key -> value. */
    public static final Map<String, TaskSection> TASKS = new LinkedHashMap<>();

    /** Keys read only at startup. Everything else is read live. */
    public static final Set<String> RESTART_KEYS = Set.of();

    private static ModConfig bound;

    public record TaskSection(BooleanValue enabled, ConfigValue<String> unlockStage, Map<String, DoubleValue> numbers) {}

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("General rules. Keys marked [preset] default to PRESET or -1 and then take the value of 'preset'.").push("general");
        PRESET = b.comment("custom = mod baseline, sandbox = easy SP (free unlock, no needs, big limits),",
                        "survival = needs on, no stages, themed = stages + needs + strict limits (story packs).")
                .defineEnum("preset", Preset.CUSTOM);
        ENABLED = b.comment("Master switch. When false workers stand idle and contracts do nothing.").define("enabled", true);
        MAX_PER_PLAYER = b.comment("[preset] Max workers one player can own. -1 = preset.").defineInRange("maxWorkersPerPlayer", -1, -1, 256);
        MAX_PER_CHUNK = b.comment("[preset] Max workers inside one chunk. -1 = preset.").defineInRange("maxWorkersPerChunk", -1, -1, 256);
        MAX_ACTIVE = b.comment("[preset] Max workers running a task at the same time on the server. -1 = preset.").defineInRange("maxActiveWorkersPerServer", -1, -1, 10000);
        WORK_OFFLINE = b.comment("Keep working while the owner is offline (only while the chunk is loaded anyway).").define("workOffline", false);
        OP_BYPASS = b.comment("Operators can command any worker and have every task unlocked.").define("opBypass", true);
        TEAM_SHARING = b.comment("Who can command a worker: OWNER, TEAM (FTB Teams, falls back to OWNER), EVERYONE.").defineEnum("teamSharing", TeamSharing.TEAM);
        ALLOW_COMMANDS = b.comment("Non-op players may use /ratworker task|stop|needs get on workers they can command.").define("allowCommands", true);
        b.pop();

        b.comment("How workers are obtained.").push("acquisition");
        CONTRACT_ENABLED = b.comment("The ratworker:contract item can recruit workers.").define("contractEnabled", true);
        CONTRACT_REQUIRES_SPOT = b.comment("The contract only works on blocks in tag #ratworker:recruit_spots (e.g. a cell bed).").define("contractRequiresSpot", false);
        REQUIRE_STAGE_TO_RECRUIT = b.comment("[preset] Recruiting needs the stage in 'recruitStage'.").defineEnum("requireStageToRecruit", TriState.PRESET);
        RECRUIT_STAGE = b.comment("Stage (KubeJS stage or scoreboard tag) needed to recruit.").define("recruitStage", "ratworker_recruit");
        NAME_MODE = b.comment("POOL = random name from the pool, CUSTOM = only named contracts work,",
                "PLAYER_CHOSEN = an anvil-renamed contract gives its name, otherwise the pool.").defineEnum("nameMode", NameMode.PLAYER_CHOSEN);
        NAME_POOL = b.comment("First names. Empty = use datapack pools (data/<ns>/ratworker/names/*.json).")
                .defineListAllowEmpty("namePool", List.of(), o -> o instanceof String);
        SKIN_POOL = b.comment("Skin textures, 'namespace:textures/....png' or with ';slim' suffix. Empty = datapack pools (ratworker/skins).")
                .defineListAllowEmpty("skinPool", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("Hunger and thirst, both 0..20. Drain values are points per minute.").push("needs");
        HUNGER_ENABLED = b.comment("[preset] Hunger drains and matters.").defineEnum("hungerEnabled", TriState.PRESET);
        THIRST_ENABLED = b.comment("[preset] Thirst drains and matters. Ignored (always off) when Thirst Was Taken is not installed.").defineEnum("thirstEnabled", TriState.PRESET);
        HUNGER_IDLE = b.comment("Hunger drain per minute with no task.").defineInRange("hungerDrainIdle", 0.2, 0, 20);
        HUNGER_WORKING = b.comment("Hunger drain per minute while a task runs, multiplied by the task's hungerCost.").defineInRange("hungerDrainWorking", 0.6, 0, 20);
        THIRST_IDLE = b.comment("Thirst drain per minute with no task.").defineInRange("thirstDrainIdle", 0.3, 0, 20);
        THIRST_WORKING = b.comment("Thirst drain per minute while a task runs, multiplied by the task's thirstCost.").defineInRange("thirstDrainWorking", 0.8, 0, 20);
        EAT_BELOW = b.comment("Auto-eat from own inventory below this hunger.").defineInRange("eatBelow", 14, 0, 20);
        DRINK_BELOW = b.comment("Auto-drink from own inventory below this thirst.").defineInRange("drinkBelow", 14, 0, 20);
        LOW_THRESHOLD = b.comment("Below this a need counts as low: work slows down and the overhead warning shows.").defineInRange("lowThreshold", 6, 0, 20);
        LOW_SLOWDOWN = b.comment("Task cooldown multiplier while a need is low.").defineInRange("lowSlowdown", 1.5, 1, 10);
        PURITY_EFFECTS = b.comment("Low purity drinks give a sickness effect. Purity: 0 dirty, 1 unsafe, 2 clean",
                "(Thirst Was Taken: dirty/slightly dirty -> 0/1, acceptable -> 1, purified -> 2).").define("purityEffects", true);
        DIRTY_EFFECT = b.comment("Effect after a purity 0 drink. Empty = none.").define("dirtyEffect", "minecraft:poison");
        DIRTY_SECONDS = b.defineInRange("dirtyEffectSeconds", 15, 0, 3600);
        UNSAFE_EFFECT = b.comment("Effect after a purity 1 drink. Empty = none.").define("unsafeEffect", "minecraft:poison");
        UNSAFE_SECONDS = b.defineInRange("unsafeEffectSeconds", 4, 0, 3600);
        DRAIN_MULTIPLIER = b.comment("Global multiplier on all hunger/thirst loss (time drain and exhaustion). Higher = harder to keep workers going.")
                .defineInRange("drainMultiplier", 1.5, 0, 20);
        ACTION_EXHAUSTION = b.comment("Share of the player exhaustion that simulated actions cause (a Create crank charges a player",
                "about 0.32 per turn, 5 turns per second). 1 = as hard as for a player.").defineInRange("actionExhaustion", 0.05, 0, 10);
        THIRST_EXHAUSTION_RATIO = b.comment("Thirst lost per point of exhaustion, relative to hunger (4 exhaustion = 1 point).")
                .defineInRange("thirstExhaustionRatio", 1.0, 0, 10);
        REGEN_ABOVE = b.comment("Workers heal while food (and water, if on) is at least this, like players at 18.").defineInRange("regenAbove", 18, 0, 20);
        REGEN_INTERVAL = b.comment("Seconds between healing 1 HP.").defineInRange("regenInterval", 4, 1, 3600);
        REGEN_EXHAUSTION = b.comment("Exhaustion per healed HP (player: 6.0; 4 = one food point).").defineInRange("regenExhaustion", 6.0, 0, 100);
        SUPPLY_RANGE = b.comment("Max distance between a worker and its supply chest, also for supply runs.").defineInRange("supplyRange", 32, 2, 256);
        SUPPLY_TAKE = b.comment("Items of food and of drink taken per supply run.").defineInRange("supplyTake", 4, 1, 64);
        STARVATION_DAMAGE = b.comment("Damage dealt when a need is at 0.").defineInRange("starvationDamage", 1.0, 0, 20);
        STARVATION_INTERVAL = b.comment("Seconds between starvation/dehydration damage.").defineInRange("starvationInterval", 10, 1, 3600);
        NEEDS_KILL = b.comment("[preset] Starvation can kill. When false it stops at 1 HP.").defineEnum("needsKill", TriState.PRESET);
        FOOD_TAG = b.comment("Item tag accepted as food. Empty = anything edible.").define("foodTag", "");
        DRINK_TAG = b.comment("Item tag accepted as drink (plus water bottles, milk, honey; Thirst Was Taken items in phase 2).").define("drinkTag", "ratworker:drinks");
        b.pop();

        b.push("death");
        PERMA_DEATH = b.comment("[preset] A dead worker is gone. When false it respawns after 'respawnCooldown'.").defineEnum("permaDeath", TriState.PRESET);
        DROP_INVENTORY = b.comment("Drop inventory and equipment on death. When false and not perma death, items come back on respawn.").define("dropInventory", true);
        OWNER_FRIENDLY_FIRE = b.comment("The owner can hurt their own workers.").define("ownerFriendlyFire", false);
        TEAM_FRIENDLY_FIRE = b.comment("Players who can command a worker can hurt it.").define("teamFriendlyFire", false);
        RESPAWN_COOLDOWN = b.comment("Seconds before a worker respawns (only when permaDeath is off).").defineInRange("respawnCooldown", 300, 0, 86400);
        b.pop();

        b.comment("Threats and environment (phase 4).").push("safety");
        RADIATION = b.comment("Mekanism radiation hurts workers like players.").define("radiation", true);
        HAZMAT_PROTECTS = b.comment("A full hazmat set protects workers from radiation.").define("hazmatProtects", true);
        FLEE_HEALTH = b.comment("Workers flee below this health.").defineInRange("fleeHealth", 6.0, 0, 1024);
        THREAT_BEHAVIOUR = b.comment("Reaction to hostiles when not guarding: FLEE, DEFEND, PASSIVE.").defineEnum("threatBehaviour", ThreatBehaviour.FLEE);
        HOSTILE_TAG = b.comment("Entity type tag of threats. Empty = any Enemy. Workers never aggro anything else.").define("hostileTag", "");
        b.pop();

        b.push("performance");
        MAX_BLOCK_SCANS = b.comment("Block checks a worker may do per tick when searching.").defineInRange("maxBlockScansPerTick", 256, 1, 65536);
        PATH_RECOMPUTE = b.comment("Ticks between path recomputations toward a moving target.").defineInRange("pathRecomputeTicks", 20, 1, 400);
        TASK_TICK_INTERVAL = b.comment("Task logic runs every N ticks.").defineInRange("taskTickInterval", 2, 1, 100);
        STUCK_TICKS = b.comment("Ticks without progress before a move counts as one failure.").defineInRange("stuckTicks", 100, 10, 2400);
        MAX_PATH_FAILURES = b.comment("Failures in a row before a task stops with a reason.").defineInRange("maxPathFailures", 5, 1, 100);
        HOLD_INTERVAL = b.comment("Ticks between re-asserted held inputs (Create cranks need <= 9).").defineInRange("holdIntervalTicks", 4, 1, 9);
        MAX_FAKE_INTERACTIONS = b.comment("Max simulated interactions per server tick, all workers together.").defineInRange("maxFakeInteractionsPerTick", 32, 1, 4096);
        b.pop();

        b.push("unlocking");
        UNLOCK_MODE = b.comment("[preset] STAGES = KubeJS stage, scoreboard tag or /ratworker unlock; OPS_ONLY = only ops;",
                "FREE = everything unlocked; NONE = only /ratworker unlock or scripts unlock.").defineEnum("mode", UnlockMode.PRESET);
        STAGE_TEMPLATE = b.comment("Stage name of a task. <task> = task id path.").define("stageTemplate", "ratworker_<task>");
        LEVEL_STAGE_TEMPLATE = b.comment("Stage name of a task level upgrade.").define("levelStageTemplate", "ratworker_<task>_<level>");
        SCOREBOARD_TAGS = b.comment("Accept scoreboard tags (/tag) with the stage name as an unlock.").define("scoreboardTags", true);
        LEVELING = b.comment("Tasks gain xp and levels from use (phase 4, data model only).").define("leveling", false);
        b.pop();

        b.comment("Training. Tasks other than innateTasks are learned from a tome (ratworker:tome with NBT {Task:'<id>'})",
                "plus worker XP (tasks.<id>.learnXp). Branches are bought with worker XP: health/strength for the worker,",
                "hunger/thirst/speed per task. Cost of level n+1 = baseCost x costGrowth^n. KubeJS can grant XP, tasks and nodes.").push("skills");
        XP_FROM_WORK = b.comment("Workers earn XP while a task runs. Off = only scripts/commands give XP.").define("xpFromWork", true);
        XP_PER_MINUTE = b.comment("XP per minute of work, multiplied by the task's xpRate.").defineInRange("xpPerMinute", 5.0, 0, 10000);
        INNATE_TASKS = b.comment("Tasks every worker knows without a tome.")
                .defineListAllowEmpty("innateTasks", List.of("ratworker:follow", "ratworker:stay"), o -> o instanceof String);
        defineBranch(b, Branch.HEALTH, 5, 20, 1.5, 2.0, "Max health +perLevel per level.");
        defineBranch(b, Branch.STRENGTH, 5, 20, 1.5, 1.0, "Attack damage +perLevel per level.");
        defineBranch(b, Branch.HUNGER, 5, 15, 1.5, 0.1, "Per task: hunger loss -perLevel (fraction) per level.");
        defineBranch(b, Branch.THIRST, 5, 15, 1.5, 0.1, "Per task: thirst loss -perLevel (fraction) per level.");
        defineBranch(b, Branch.SPEED, 6, 15, 1.5, 2.0, "Per task: -perLevel ticks between clicks (crank holdInterval) or faster work cooldowns.");
        b.pop();

        b.comment("Per task tuning. Number keys: -1 = datapack/task default.").push("tasks");
        BuiltinTasks.DEFAULTS.forEach((path, numbers) -> {
            b.push(path);
            BooleanValue en = b.define("enabled", true);
            ConfigValue<String> st = b.comment("Unlock stage. Empty = unlocking.stageTemplate.").define("unlockStage", "");
            Map<String, DoubleValue> nums = new LinkedHashMap<>();
            for (String key : numbers.asMap().keySet()) {
                nums.put(key, b.defineInRange(key, -1.0, -1.0, 1_000_000.0));
            }
            TASKS.put(path, new TaskSection(en, st, nums));
            b.pop();
        });
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {}

    private static void defineBranch(ForgeConfigSpec.Builder b, Branch br, int max, double base, double growth, double per, String what) {
        b.comment(what).push(br.key());
        BRANCHES.put(br, new BranchSection(
                b.defineInRange("maxLevel", max, 0, 100),
                b.defineInRange("baseCost", base, 0, 1_000_000),
                b.defineInRange("costGrowth", growth, 1, 10),
                b.defineInRange("perLevel", per, 0, 1000)));
        b.pop();
    }

    public static void bind(ModConfig config) {
        bound = config;
    }

    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    /** Re-reads the file from disk. Returns false if the config is not file backed. */
    public static boolean reloadFromDisk() {
        if (bound != null && bound.getConfigData() instanceof FileConfig file) {
            file.load();
            SPEC.afterReload();
            return true;
        }
        return false;
    }

    /** All leaf keys as dotted paths, for /ratworker config. */
    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        collect(SPEC.getValues().valueMap(), "", out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Map<String, Object> map, String prefix, List<String> out) {
        map.forEach((k, v) -> {
            String path = prefix.isEmpty() ? k : prefix + "." + k;
            if (v instanceof com.electronwill.nightconfig.core.UnmodifiableConfig sub) collect((Map<String, Object>) sub.valueMap(), path, out);
            else out.add(path);
        });
    }

    public static ConfigValue<?> value(String path) {
        Object o = SPEC.getValues().get(path);
        return o instanceof ConfigValue<?> cv ? cv : null;
    }

    /** Sets a value from text. Throws IllegalArgumentException with a readable message. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void set(String path, String text) {
        ConfigValue cv = value(path);
        if (cv == null) throw new IllegalArgumentException("Unknown key: " + path);
        Object current = cv.get();
        Object parsed;
        try {
            if (current instanceof Boolean) parsed = Boolean.parseBoolean(text);
            else if (current instanceof Integer) parsed = Integer.parseInt(text);
            else if (current instanceof Double) parsed = Double.parseDouble(text);
            else if (current instanceof Enum<?> e) parsed = Enum.valueOf(e.getDeclaringClass(), text.toUpperCase(java.util.Locale.ROOT));
            else if (current instanceof List) parsed = text.isBlank() ? List.of() : List.of(text.split(","));
            else parsed = text;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Bad value '" + text + "' for " + path);
        }
        ForgeConfigSpec.ValueSpec spec = (ForgeConfigSpec.ValueSpec) SPEC.getSpec().get(path);
        if (spec != null && !spec.test(parsed)) throw new IllegalArgumentException("Out of range: " + text);
        cv.set(parsed);
        cv.save();
    }

    public static Map<String, Double> numbersFor(String taskPath) {
        TaskSection s = TASKS.get(taskPath);
        Map<String, Double> out = new LinkedHashMap<>();
        if (s != null) s.numbers.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
