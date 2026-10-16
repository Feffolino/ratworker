package it.ratlab.ratworker.compat.kubejs;

import it.ratlab.ratworker.api.InnateTask;
import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.TaskContext;
import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.skill.Branch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * KubeJS task definition:
 * <pre>
 * WorkerEvents.registerTasks(event => {
 *   event.create('chop').title('Chop wood').icon('minecraft:iron_axe')
 *     .cost(1.2, 1.0).range(10).learnXp(40).requires('#minecraft:axes')
 *     .onTick(ctx => { ... })
 * })
 * </pre>
 */
public class ScriptTaskBuilder {
    private final ResourceLocation id;
    private Component title, description;
    private ItemStack icon = new ItemStack(Items.PAPER);
    private final TaskNumbers numbers = TaskNumbers.of(1, 1, 1, 16, 1);
    private String stage;
    private boolean innate, work = true;
    private Ingredient requires;
    private final List<Branch> branches = new ArrayList<>(List.of(Branch.HUNGER, Branch.THIRST));
    private Function<ScriptTaskContext, Object> canStart;
    private Consumer<ScriptTaskContext> onStart, onTick, onStop;

    ScriptTaskBuilder(ResourceLocation id) {
        this.id = id;
    }

    public ScriptTaskBuilder title(Component c) { title = c; return this; }
    public ScriptTaskBuilder description(Component c) { description = c; return this; }
    public ScriptTaskBuilder icon(ItemStack s) { icon = s; return this; }
    /** Hunger and thirst cost multipliers while running. */
    public ScriptTaskBuilder cost(double hunger, double thirst) { numbers.with(TaskNumbers.HUNGER_COST, hunger).with(TaskNumbers.THIRST_COST, thirst); return this; }
    public ScriptTaskBuilder range(double r) { numbers.with(TaskNumbers.RANGE, r); return this; }
    public ScriptTaskBuilder speed(double s) { numbers.with(TaskNumbers.SPEED, s); return this; }
    public ScriptTaskBuilder learnXp(double xp) { numbers.with(TaskNumbers.LEARN_XP, xp); return this; }
    public ScriptTaskBuilder xpRate(double r) { numbers.with(TaskNumbers.XP_RATE, r); return this; }
    /** Any extra number, readable with ctx.number(key) and overridable from the datapack task file. */
    public ScriptTaskBuilder number(String key, double value) { numbers.with(key, value); return this; }
    /** Stage name, used only in unlocking.mode STAGES. */
    public ScriptTaskBuilder stage(String s) { stage = s; return this; }
    /** Known by every worker without a tome. */
    public ScriptTaskBuilder innate(boolean b) { innate = b; return this; }
    /** Idle-type task (drains at the idle rate, like stay). */
    public ScriptTaskBuilder idle(boolean b) { work = !b; return this; }
    /** The worker must hold or carry a matching item to start. */
    public ScriptTaskBuilder requires(Ingredient i) { requires = i; return this; }
    /** Adds the SPEED branch (click/work speed) to this task's upgrades. */
    public ScriptTaskBuilder speedBranch(boolean b) { branches.remove(Branch.SPEED); if (b) branches.add(Branch.SPEED); return this; }
    /** Return a string (reason) to refuse the start, or nothing to allow it. */
    public ScriptTaskBuilder canStart(Function<ScriptTaskContext, Object> f) { canStart = f; return this; }
    public ScriptTaskBuilder onStart(Consumer<ScriptTaskContext> f) { onStart = f; return this; }
    public ScriptTaskBuilder onTick(Consumer<ScriptTaskContext> f) { onTick = f; return this; }
    public ScriptTaskBuilder onStop(Consumer<ScriptTaskContext> f) { onStop = f; return this; }

    WorkerTask build() {
        return new Task(this);
    }

    private static final class Task implements WorkerTask, InnateTask {
        private final ScriptTaskBuilder b;

        Task(ScriptTaskBuilder b) {
            this.b = b;
        }

        @Override public ResourceLocation id() { return b.id; }
        @Override public Component title() { return b.title != null ? b.title : Component.translatable(langKey()); }
        @Override public Component description() { return b.description != null ? b.description : Component.translatable(langKey() + ".desc"); }
        @Override public ItemStack icon() { return b.icon.copy(); }
        @Override public TaskNumbers defaults() { return b.numbers; }
        @Override public String stageKey() { return b.stage; }
        @Override public boolean innate() { return b.innate; }
        @Override public boolean isWork() { return b.work; }
        @Override public List<Branch> branches() { return b.branches; }

        @Override
        public TaskCheck canStart(TaskContext ctx) {
            if (b.requires != null && !ScriptTaskContext.has(ctx.worker(), b.requires)) {
                ItemStack[] items = b.requires.getItems();
                Component what = items.length > 0 ? items[0].getHoverName() : Component.literal("?");
                return TaskCheck.fail("ratworker.reason.needs_item", what);
            }
            if (b.canStart == null) return TaskCheck.OK;
            Object r = b.canStart.apply(new ScriptTaskContext(ctx));
            if (r == null || r instanceof Boolean ok && ok) return TaskCheck.OK;
            if (r instanceof Boolean) return TaskCheck.fail("ratworker.reason.script_refused");
            return TaskCheck.fail(r instanceof Component c ? c : Component.literal(String.valueOf(r)));
        }

        @Override
        public void start(TaskContext ctx) {
            if (b.onStart != null) b.onStart.accept(new ScriptTaskContext(ctx));
        }

        @Override
        public void tick(TaskContext ctx) {
            if (b.onTick != null) b.onTick.accept(new ScriptTaskContext(ctx));
        }

        @Override
        public void stop(TaskContext ctx) {
            if (b.onStop != null) b.onStop.accept(new ScriptTaskContext(ctx));
            ctx.input().releaseAll();
        }
    }
}
