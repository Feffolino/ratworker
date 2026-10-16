package it.ratlab.ratworker.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.ratlab.ratworker.RatWorker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Datapack layer, reloaded with /reload (also from kubejs/data/):
 * <ul>
 *   <li>{@code data/<ns>/ratworker/names/*.json}: {"names": ["Ada", ...]} merged into one pool</li>
 *   <li>{@code data/<ns>/ratworker/skins/*.json}: {"skins": [{"texture": "ns:textures/...png", "slim": false}]}</li>
 *   <li>{@code data/<ns>/ratworker/task/<id>.json}: numbers of task &lt;ns&gt;:&lt;id&gt;, e.g. {"hungerCost": 1.2, "range": 10}</li>
 *   <li>{@code data/<ns>/ratworker/drink/<any>.json}: {"item": "minecraft:potion", "potion": "minecraft:water", "thirst": 6, "purity": 1}
 *       (potion and purity optional; purity 0..2, missing = the item's own Thirst Was Taken purity)</li>
 * </ul>
 * To replace a shipped pool, override the same file path (e.g. data/ratworker/ratworker/names/default.json).
 */
public class WorkerDataLoader extends SimplePreparableReloadListener<WorkerDataLoader.Data> {
    public static final WorkerDataLoader INSTANCE = new WorkerDataLoader();

    public record Skin(ResourceLocation texture, boolean slim) {}

    /** potion may be null; purity -1 = take it from the stack (Thirst Was Taken NBT) or default. */
    public record Drink(ResourceLocation item, ResourceLocation potion, int thirst, int purity) {}

    public record Data(List<String> names, List<Skin> skins, Map<ResourceLocation, Map<String, Double>> taskNumbers, List<Drink> drinks) {}

    private volatile Data data = new Data(List.of(), List.of(), Map.of(), List.of());

    public Data data() {
        return data;
    }

    @Override
    protected Data prepare(ResourceManager rm, ProfilerFiller profiler) {
        List<String> names = new ArrayList<>();
        List<Skin> skins = new ArrayList<>();
        Map<ResourceLocation, Map<String, Double>> tasks = new HashMap<>();
        List<Drink> drinks = new ArrayList<>();

        forEachJson(rm, "ratworker/names", (id, json) -> {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "names")) names.add(e.getAsString());
        });
        forEachJson(rm, "ratworker/skins", (id, json) -> {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "skins")) {
                JsonObject o = e.getAsJsonObject();
                skins.add(new Skin(ResourceLocation.parse(GsonHelper.getAsString(o, "texture")), GsonHelper.getAsBoolean(o, "slim", false)));
            }
        });
        forEachJson(rm, "ratworker/task", (id, json) -> {
            String path = id.getPath();
            path = path.substring("ratworker/task/".length(), path.length() - ".json".length());
            Map<String, Double> nums = new HashMap<>();
            for (var en : json.entrySet()) {
                if (en.getValue().isJsonPrimitive() && en.getValue().getAsJsonPrimitive().isNumber()) {
                    nums.put(en.getKey(), en.getValue().getAsDouble());
                }
            }
            tasks.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path), nums);
        });
        forEachJson(rm, "ratworker/drink", (id, json) -> {
            String potion = GsonHelper.getAsString(json, "potion", "");
            drinks.add(new Drink(ResourceLocation.parse(GsonHelper.getAsString(json, "item")),
                    potion.isEmpty() ? null : ResourceLocation.parse(potion),
                    GsonHelper.getAsInt(json, "thirst"), GsonHelper.getAsInt(json, "purity", -1)));
        });
        return new Data(List.copyOf(names), List.copyOf(skins), Map.copyOf(tasks), List.copyOf(drinks));
    }

    @Override
    protected void apply(Data prepared, ResourceManager rm, ProfilerFiller profiler) {
        data = prepared;
        // server scripts are reloaded before datapack listeners apply, so WorkerEvents.registerTasks is current
        it.ratlab.ratworker.compat.Compat.kubeReloadTasks();
        RatWorker.LOG.info("Rat Worker data: {} names, {} skins, {} task overrides, {} drinks",
                prepared.names().size(), prepared.skins().size(), prepared.taskNumbers().size(), prepared.drinks().size());
    }

    private interface JsonConsumer {
        void accept(ResourceLocation id, JsonObject json);
    }

    private static void forEachJson(ResourceManager rm, String folder, JsonConsumer consumer) {
        Map<ResourceLocation, Resource> found = new TreeMap<>(rm.listResources(folder, rl -> rl.getPath().endsWith(".json")));
        found.forEach((id, res) -> {
            try (Reader r = res.openAsReader()) {
                consumer.accept(id, JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception ex) {
                RatWorker.LOG.error("Rat Worker: bad data file {}: {}", id, ex.getMessage());
            }
        });
    }
}
