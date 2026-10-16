package it.ratlab.ratworker.entity;

import it.ratlab.ratworker.api.TaskNumbers;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.skill.Branch;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.task.TaskRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;

/** Hunger and thirst, 0..20, drained once per second on the server. */
public class WorkerNeeds {
    public static final float MAX = 20f;

    private float hunger = MAX, thirst = MAX;
    private int starveTimer;
    private float exhaustion, thirstExhaustion;

    /** Player-style exhaustion (4 = one point), from actions, damage and the Hunger effect. */
    public void addExhaustion(float amount) {
        if (amount <= 0) return;
        exhaustion += amount;
        thirstExhaustion += amount * ServerConfig.THIRST_EXHAUSTION_RATIO.get().floatValue();
    }



    public float hunger() {
        return hunger;
    }

    public float thirst() {
        return thirst;
    }

    public void set(float hunger, float thirst) {
        this.hunger = clamp(hunger);
        this.thirst = clamp(thirst);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(MAX, v));
    }

    public boolean anyEmpty() {
        return (Settings.hungerEnabled() && hunger <= 0) || (Settings.thirstEnabled() && thirst <= 0);
    }

    public boolean anyLow() {
        int low = ServerConfig.LOW_THRESHOLD.get();
        return (Settings.hungerEnabled() && hunger < low) || (Settings.thirstEnabled() && thirst < low);
    }

    public Component emptyReason() {
        return Component.translatable(Settings.hungerEnabled() && hunger <= 0 ? "ratworker.reason.too_hungry" : "ratworker.reason.too_thirsty");
    }

    void tickSecond(WorkerEntity w) {
        boolean hungerOn = Settings.hungerEnabled(), thirstOn = Settings.thirstEnabled();
        WorkerTask task = w.runner().task();
        boolean working = task != null && task.isWork() && !w.runner().isPaused();
        double hungerFactor = ServerConfig.DRAIN_MULTIPLIER.get() * w.skills().drainFactor(Branch.HUNGER, task);
        double thirstFactor = ServerConfig.DRAIN_MULTIPLIER.get() * w.skills().drainFactor(Branch.THIRST, task);
        if (working && ServerConfig.XP_FROM_WORK.get()) {
            int gained = w.skills().accrue(ServerConfig.XP_PER_MINUTE.get() * TaskRules.number(task, TaskNumbers.XP_RATE) / 60.0);
            if (gained > 0) w.gainXp(gained, task);
        }
        var hungerEffect = w.getEffect(net.minecraft.world.effect.MobEffects.HUNGER);
        if (hungerEffect != null) addExhaustion(0.1f * (hungerEffect.getAmplifier() + 1)); // vanilla: 0.005 per tick per level
        if (hungerOn) {
            double rate = working ? ServerConfig.HUNGER_WORKING.get() : ServerConfig.HUNGER_IDLE.get();
            if (task != null) rate *= TaskRules.number(task, TaskNumbers.HUNGER_COST);
            hunger = clamp(hunger - (float) (rate * hungerFactor / 60.0));
            while (exhaustion >= 4f) {
                exhaustion -= 4f;
                hunger = clamp(hunger - (float) hungerFactor);
            }
        }
        if (thirstOn) {
            double rate = working ? ServerConfig.THIRST_WORKING.get() : ServerConfig.THIRST_IDLE.get();
            if (task != null) rate *= TaskRules.number(task, TaskNumbers.THIRST_COST);
            thirst = clamp(thirst - (float) (rate * thirstFactor / 60.0));
            while (thirstExhaustion >= 4f) {
                thirstExhaustion -= 4f;
                thirst = clamp(thirst - (float) thirstFactor);
            }
        }
        if (!hungerOn) exhaustion = 0;
        if (!thirstOn) thirstExhaustion = 0;
        if (thirstOn && thirst < ServerConfig.DRINK_BELOW.get()) tryDrink(w);
        if (hungerOn && hunger < ServerConfig.EAT_BELOW.get()) tryEat(w);

        if ((hungerOn && hunger <= 0) || (thirstOn && thirst <= 0)) {
            if (++starveTimer >= ServerConfig.STARVATION_INTERVAL.get()) {
                starveTimer = 0;
                float dmg = ServerConfig.STARVATION_DAMAGE.get().floatValue();
                if (!Settings.needsKill()) dmg = Math.min(dmg, w.getHealth() - 1f);
                if (dmg > 0) w.hurt(w.damageSources().starve(), dmg);
            }
        } else {
            starveTimer = 0;
        }
    }

    // ---- food and drink ----

    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty() || (Settings.thirstEnabled() && isDrink(stack))) return false; // honey stays food without thirst
        String tag = ServerConfig.FOOD_TAG.get();
        if (tag.isBlank()) return stack.isEdible();
        ResourceLocation id = ResourceLocation.tryParse(tag);
        return id != null && stack.is(TagKey.create(Registries.ITEM, id));
    }

    public static boolean isDrink(ItemStack stack) {
        return Drinks.of(stack) != null;
    }

    private void tryEat(WorkerEntity w) {
        var inv = w.inventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isFood(s)) continue;
            FoodProperties fp = s.getFoodProperties(w);
            if (fp == null) continue;
            Item item = s.getItem();
            hunger = clamp(hunger + fp.getNutrition());
            if (Settings.thirstEnabled()) thirst = clamp(thirst + Compat.twtFoodThirst(s)); // fruit, soups
            w.eat(w.level(), s); // plays the sound, applies food effects, shrinks the stack
            if (item instanceof BowlFoodItem || item instanceof SuspiciousStewItem) w.giveOrDrop(new ItemStack(Items.BOWL));
            inv.setChanged();
            return;
        }
    }

    /** Drinks the cleanest drink in the inventory. */
    private void tryDrink(WorkerEntity w) {
        var inv = w.inventory();
        int best = -1;
        Drinks.Info bestInfo = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            Drinks.Info info = Drinks.of(inv.getItem(i));
            if (info != null && info.thirst() > 0 && (bestInfo == null || info.purity() > bestInfo.purity())) {
                best = i;
                bestInfo = info;
            }
        }
        if (bestInfo == null) return;
        ItemStack remainder = Drinks.consume(w, inv.getItem(best));
        thirst = clamp(thirst + bestInfo.thirst());
        Drinks.applyPurity(w, bestInfo.purity());
        if (!remainder.isEmpty()) w.giveOrDrop(remainder);
        inv.setChanged();
    }

    public boolean hasFood(WorkerEntity w) {
        for (int i = 0; i < w.inventory().getContainerSize(); i++) if (isFood(w.inventory().getItem(i))) return true;
        return false;
    }

    public boolean hasDrink(WorkerEntity w) {
        for (int i = 0; i < w.inventory().getContainerSize(); i++) if (isDrink(w.inventory().getItem(i))) return true;
        return false;
    }

    /** True when a need is below its eat/drink threshold and the inventory has nothing for it. */
    public boolean wantsSupplies(WorkerEntity w) {
        boolean food = Settings.hungerEnabled() && hunger < ServerConfig.EAT_BELOW.get() && !hasFood(w);
        boolean drink = Settings.thirstEnabled() && thirst < ServerConfig.DRINK_BELOW.get() && !hasDrink(w);
        return food || drink;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("Hunger", hunger);
        t.putFloat("Thirst", thirst);
        t.putFloat("Exhaustion", exhaustion);
        t.putFloat("ThirstExhaustion", thirstExhaustion);
        return t;
    }

    public void load(CompoundTag t) {
        hunger = t.contains("Hunger") ? clamp(t.getFloat("Hunger")) : MAX;
        thirst = t.contains("Thirst") ? clamp(t.getFloat("Thirst")) : MAX;
        exhaustion = t.getFloat("Exhaustion");
        thirstExhaustion = t.getFloat("ThirstExhaustion");
    }
}
