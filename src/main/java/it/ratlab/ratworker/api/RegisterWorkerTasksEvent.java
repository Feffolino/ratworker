package it.ratlab.ratworker.api;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Fired once on every mod bus during common setup. Addons register their tasks here:
 * <pre>{@code
 * modBus.addListener((RegisterWorkerTasksEvent e) -> e.register(new MyTask()));
 * }</pre>
 */
public class RegisterWorkerTasksEvent extends Event implements IModBusEvent {
    private final WorkerTaskRegistry registry;

    public RegisterWorkerTasksEvent(WorkerTaskRegistry registry) {
        this.registry = registry;
    }

    public void register(WorkerTask task) {
        registry.register(task);
    }
}
