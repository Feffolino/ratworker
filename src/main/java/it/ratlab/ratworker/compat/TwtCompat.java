package it.ratlab.ratworker.compat;

import dev.ghen.thirst.api.ThirstHelper;
import net.minecraft.world.item.ItemStack;

/** Loaded only when Thirst Was Taken is present. Same values TWT gives players. */
final class TwtCompat {
    private TwtCompat() {}

    /**
     * Per-use value as registered with TWT. ThirstHelper.getThirst is not used for the amount because
     * thirstcanteen patches it to return the whole canteen (sips x 6); one use is one sip.
     */
    static int[] drink(ItemStack stack) {
        if (!ThirstHelper.isDrink(stack)) return null;
        if (stack.hasTag() && stack.getTag().contains("Contain") && stack.getTag().getInt("Contain") <= 0) return null; // empty canteen
        Number[] v = ThirstHelper.VALID_DRINKS.get(stack.getItem());
        int thirst = v != null && v.length > 0 ? v[0].intValue() : ThirstHelper.getThirst(stack);
        return new int[]{thirst, ThirstHelper.getPurity(stack)};
    }

    static int foodThirst(ItemStack stack) {
        return ThirstHelper.isFood(stack) ? ThirstHelper.getThirst(stack) : 0;
    }
}
