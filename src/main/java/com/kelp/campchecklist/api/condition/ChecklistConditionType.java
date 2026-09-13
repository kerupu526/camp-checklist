package com.kelp.campchecklist.api.condition;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import com.kelp.campchecklist.api.invalidation.ConditionDependency;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A registered condition's configuration, tracking semantics, evaluator and static display. */
public interface ChecklistConditionType<C> {
    Codec<C> codec();
    ConditionEvaluator<C> evaluator();
    /**
     * Declares exact world-state dependencies used to schedule reevaluation.
     * This is optimization metadata only; failures never change evaluation or storage semantics.
     */
    default Collection<ConditionDependency> dependencies(C config) { return List.of(); }
    /** Defaults to all encoded config. Override only to omit fields safe for state reuse. */
    default JsonElement trackingMaterial(C config) {
        return codec().encodeStart(JsonOps.INSTANCE, config).getOrThrow();
    }
    /** Storage layout only. A change resets this node's opaque tag, not numeric tracking identity. */
    default int stateVersion() { return 0; }
    /** Whether this condition requires the future persistent node-state backend. */
    default boolean usesPersistentState(C config) { return false; }
    /** Historical/event/sticky conditions must leave this false. */
    default boolean supportsNegation(C config) { return false; }
    default ConditionDisplay display(C config) {
        return new ConditionDisplay(Component.translatable("camp_checklist.condition.generic"), Optional.empty());
    }
}
