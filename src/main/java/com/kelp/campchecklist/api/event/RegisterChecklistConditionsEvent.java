package com.kelp.campchecklist.api.event;

import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.registry.ConditionRegistrar;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import java.util.Objects;

/** Synchronous mod-bus event during common setup's queued work; no server instance is required. */
public final class RegisterChecklistConditionsEvent extends Event implements IModBusEvent {
    private final ConditionRegistrar registrar;
    public RegisterChecklistConditionsEvent(ConditionRegistrar registrar) { this.registrar = Objects.requireNonNull(registrar); }
    /** Duplicate IDs and registration after this phase are hard errors. */
    public void register(ResourceLocation id, ChecklistConditionType<?> type) { registrar.register(id, type); }
}
