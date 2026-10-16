package it.ratlab.ratworker.entity;

import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.data.WorkerDataLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * What a worker can drink and how clean it is. Purity: 0 dirty, 1 unsafe, 2 clean.
 * Order: datapack ratworker/drink entries, then Thirst Was Taken's own values, then vanilla defaults
 * and the drink tag. Only used while thirst is enabled (which needs Thirst Was Taken).
 */
public final class Drinks {
    private Drinks() {}

    public record Info(int thirst, int purity) {}

    @Nullable
    public static Info of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        int[] twt = Compat.twtDrink(stack);
        for (WorkerDataLoader.Drink d : WorkerDataLoader.INSTANCE.data().drinks()) {
            if (!stack.is(ForgeRegistries.ITEMS.getValue(d.item()))) continue;
            if (d.potion() != null && !d.potion().equals(ForgeRegistries.POTIONS.getKey(PotionUtils.getPotion(stack)))) continue;
            int purity = d.purity() >= 0 ? d.purity() : twt != null ? fromTwt(twt[1]) : 2;
            return new Info(d.thirst(), clampPurity(purity));
        }
        if (twt != null && twt[0] > 0) return new Info(twt[0], fromTwt(twt[1]));
        if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER) return new Info(6, 1);
        if (stack.is(Items.MILK_BUCKET)) return new Info(5, 2);
        if (stack.is(Items.HONEY_BOTTLE)) return new Info(3, 2);
        ResourceLocation tag = ResourceLocation.tryParse(ServerConfig.DRINK_TAG.get());
        if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return new Info(4, 2);
        return null;
    }

    /** TWT 0 dirty, 1 slightly dirty, 2 acceptable, 3 purified. */
    public static int fromTwt(int p) {
        return p >= 3 ? 2 : p >= 1 ? 1 : 0;
    }

    private static int clampPurity(int p) {
        return Math.max(0, Math.min(2, p));
    }

    /**
     * Drinks one item from the stack (in place). Returns the container left over (bottle, bucket, bowl),
     * which the caller puts back. Vanilla potion/milk/honey do not shrink for non-players, so they are
     * handled by hand. Other drinks (Thirst Was Taken bowls, thirstcanteen canteens, which need a player)
     * run their own finishUsingItem with the worker's fake player; whatever it hands back is collected.
     */
    public static ItemStack consume(WorkerEntity w, ItemStack s) {
        ItemStack remainder;
        if (s.is(Items.POTION)) {
            s.shrink(1);
            remainder = new ItemStack(Items.GLASS_BOTTLE);
        } else if (s.is(Items.MILK_BUCKET)) {
            w.removeAllEffects();
            s.shrink(1);
            remainder = new ItemStack(Items.BUCKET);
        } else if (s.is(Items.HONEY_BOTTLE)) {
            w.removeEffect(MobEffects.POISON);
            s.shrink(1);
            remainder = new ItemStack(Items.GLASS_BOTTLE);
        } else {
            ItemStack before = s.copy();
            ItemStack container = s.hasCraftingRemainingItem() ? s.getCraftingRemainingItem() : ItemStack.EMPTY;
            FakePlayer fp = w.input().fakePlayer();
            fp.setItemInHand(InteractionHand.MAIN_HAND, s);
            ItemStack result;
            try {
                result = s.finishUsingItem(w.level(), fp);
            } finally {
                fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            // items the drink put into the "player" inventory (canteen -> empty canteen)
            Inventory inv = fp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack extra = inv.removeItemNoUpdate(i);
                if (!extra.isEmpty()) w.giveOrDrop(extra);
            }
            if (result != s) {
                remainder = result; // the item turned itself into its container
                s.setCount(0);
            } else {
                // nothing changed at all: never an endless drink
                if (ItemStack.matches(before, s)) {
                    s.shrink(1);
                    remainder = container;
                } else {
                    remainder = ItemStack.EMPTY;
                }
            }
        }
        w.playSound(SoundEvents.GENERIC_DRINK, 0.5f, 1f);
        return remainder;
    }

    public static void applyPurity(WorkerEntity w, int purity) {
        if (!ServerConfig.PURITY_EFFECTS.get() || purity >= 2) return;
        String id = purity == 0 ? ServerConfig.DIRTY_EFFECT.get() : ServerConfig.UNSAFE_EFFECT.get();
        int seconds = purity == 0 ? ServerConfig.DIRTY_SECONDS.get() : ServerConfig.UNSAFE_SECONDS.get();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        MobEffect effect = rl == null || id.isBlank() ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
        if (effect != null && seconds > 0) w.addEffect(new MobEffectInstance(effect, seconds * 20));
    }
}
