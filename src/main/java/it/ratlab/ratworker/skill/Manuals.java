package it.ratlab.ratworker.skill;

import it.ratlab.ratworker.data.WorkerDataLoader;
import it.ratlab.ratworker.item.TomeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Which items teach which task. Three sources:
 * <ul>
 *   <li>the built-in {@code ratworker:tome} with NBT {Task:"id"} (optional {Xp:n} overrides the XP cost);</li>
 *   <li>datapack {@code data/<ns>/ratworker/manual/<any>.json}: {"item": "ratlab:manual_crank", "task": "ratworker:crank", "xp": 0};</li>
 *   <li>KubeJS {@code WorkerEvents.manuals(e => e.add('ratlab:manual_crank', 'ratworker:crank', 0))}.</li>
 * </ul>
 * Any item can be a manual, so packs make their own (KubeJS items, own textures, own recipes; JEI shows them).
 * xp = -1 uses the task's learnXp.
 */
public final class Manuals {
    public record Manual(ResourceLocation task, int xp) {}

    private static final Map<ResourceLocation, Manual> SCRIPTED = new HashMap<>();

    private Manuals() {}

    public static void clearScripted() {
        SCRIPTED.clear();
    }

    public static void registerScripted(ResourceLocation item, ResourceLocation task, int xp) {
        SCRIPTED.put(item, new Manual(task, xp));
    }

    @Nullable
    public static Manual of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof TomeItem) {
            ResourceLocation task = TomeItem.taskId(stack);
            if (task == null) return null;
            CompoundTag t = stack.getTag();
            return new Manual(task, t != null && t.contains("Xp") ? t.getInt("Xp") : -1);
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        Manual m = SCRIPTED.get(id);
        if (m != null) return m;
        for (WorkerDataLoader.ManualDef d : WorkerDataLoader.INSTANCE.data().manuals()) {
            if (d.item().equals(id)) return new Manual(d.task(), d.xp());
        }
        return null;
    }
}
