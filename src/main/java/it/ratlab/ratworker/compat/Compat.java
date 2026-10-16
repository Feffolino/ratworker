package it.ratlab.ratworker.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

/**
 * Entry points for soft dependencies. Each mod-specific class is touched only after its mod id is
 * confirmed loaded, so its imports never resolve when the mod is absent.
 */
public final class Compat {
    private static Boolean kubejs, ftbTeams, thirst;

    private Compat() {}

    public static boolean kubejs() {
        if (kubejs == null) kubejs = ModList.get().isLoaded("kubejs");
        return kubejs;
    }

    /** Thirst Was Taken. Without it workers have no thirst at all. */
    public static boolean thirst() {
        if (thirst == null) thirst = ModList.get().isLoaded("thirst");
        return thirst;
    }

    public static boolean ftbTeams() {
        if (ftbTeams == null) ftbTeams = ModList.get().isLoaded("ftbteams");
        return ftbTeams;
    }

    public static boolean hasKubeJSStage(ServerPlayer player, String stage) {
        return kubejs() && KubeJSStages.has(player, stage);
    }

    /** {thirst, purity 0..3} from Thirst Was Taken, or null if not a TWT drink. */
    public static int[] twtDrink(net.minecraft.world.item.ItemStack stack) {
        return thirst() ? TwtCompat.drink(stack) : null;
    }

    /** Thirst restored by a TWT hydrating food (fruit, soup), 0 if none. */
    public static int twtFoodThirst(net.minecraft.world.item.ItemStack stack) {
        return thirst() ? TwtCompat.foodThirst(stack) : 0;
    }

    public static double kubeGainXp(it.ratlab.ratworker.entity.WorkerEntity w, it.ratlab.ratworker.api.WorkerTask task, double amount) {
        return kubejs() ? it.ratlab.ratworker.compat.kubejs.WorkerEventsJS.gainXp(w, task, amount) : amount;
    }

    public static boolean kubeLearn(it.ratlab.ratworker.entity.WorkerEntity w, it.ratlab.ratworker.api.WorkerTask task, ServerPlayer player) {
        return !kubejs() || it.ratlab.ratworker.compat.kubejs.WorkerEventsJS.learn(w, task, player);
    }

    /** Rebuilds script tasks after server scripts (re)load. */
    public static void kubeReloadTasks() {
        if (kubejs()) it.ratlab.ratworker.compat.kubejs.WorkerEventsJS.reloadTasks();
    }

    public static boolean sameTeam(UUID a, UUID b) {
        return ftbTeams() && FtbTeamsCompat.sameTeam(a, b);
    }
}
