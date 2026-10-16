package it.ratlab.ratworker.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All known tasks. Java tasks are added during {@link RegisterWorkerTasksEvent}; script tasks
 * (KubeJS, phase 3) are added and cleared on each server script reload through {@link #registerScripted}.
 */
public final class WorkerTaskRegistry {
    public static final WorkerTaskRegistry INSTANCE = new WorkerTaskRegistry();

    private final Map<ResourceLocation, WorkerTask> java = new LinkedHashMap<>();
    private final Map<ResourceLocation, WorkerTask> scripted = new LinkedHashMap<>();

    private WorkerTaskRegistry() {}

    public synchronized void register(WorkerTask task) {
        if (java.putIfAbsent(task.id(), task) != null) {
            throw new IllegalStateException("Duplicate worker task id " + task.id());
        }
    }

    public synchronized void registerScripted(WorkerTask task) {
        scripted.put(task.id(), task);
    }

    public synchronized void clearScripted() {
        scripted.clear();
    }

    @Nullable
    public WorkerTask get(ResourceLocation id) {
        WorkerTask t = java.get(id);
        return t != null ? t : scripted.get(id);
    }

    public Collection<WorkerTask> all() {
        Map<ResourceLocation, WorkerTask> out = new LinkedHashMap<>(java);
        scripted.forEach(out::putIfAbsent);
        return Collections.unmodifiableCollection(out.values());
    }
}
