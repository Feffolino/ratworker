package it.ratlab.ratworker.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Listed in kubejs.plugins.txt; KubeJS only loads it when KubeJS is installed. */
public class RatWorkerKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        WorkerEventsJS.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.SERVER) event.add("RatWorker", RatWorkerJS.INSTANCE);
    }
}
