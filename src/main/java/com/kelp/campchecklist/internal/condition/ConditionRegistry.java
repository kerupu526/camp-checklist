package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.event.RegisterChecklistConditionsEvent;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Internal registry, writable only inside a single synchronous event dispatch. */
public final class ConditionRegistry {
    private final Map<ResourceLocation, ChecklistConditionType<?>> types = new LinkedHashMap<>();
    private boolean registering, frozen;
    public synchronized void initialize(Consumer<RegisterChecklistConditionsEvent> dispatch) {
        if (registering || frozen) throw new IllegalStateException("Condition registration already started");
        registering = true;
        try { dispatch.accept(new RegisterChecklistConditionsEvent(this::register)); }
        finally { registering = false; frozen = true; }
    }
    private synchronized void register(ResourceLocation id, ChecklistConditionType<?> type) {
        if (!registering || frozen) throw new IllegalStateException("Condition registry is frozen");
        Objects.requireNonNull(id); Objects.requireNonNull(type);
        if (type.stateVersion() < 0) throw new IllegalArgumentException("Negative opaque state version: " + id);
        if (types.putIfAbsent(id, type) != null) throw new IllegalArgumentException("Duplicate condition type: " + id);
    }
    public synchronized ChecklistConditionType<?> get(ResourceLocation id) { return types.get(id); }
    public synchronized boolean frozen() { return frozen; }
    public synchronized int size() { return types.size(); }
}
