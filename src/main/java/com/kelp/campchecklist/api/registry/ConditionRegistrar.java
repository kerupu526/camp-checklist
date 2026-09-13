package com.kelp.campchecklist.api.registry;

import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import net.minecraft.resources.ResourceLocation;

/** Registration callback supplied only for the duration of RegisterChecklistConditionsEvent. */
@FunctionalInterface
public interface ConditionRegistrar {
    void register(ResourceLocation id, ChecklistConditionType<?> type);
}
