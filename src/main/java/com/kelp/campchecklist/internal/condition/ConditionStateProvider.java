package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.api.progress.ConditionStateHandle;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Internal state boundary used by the engine; implementations own persistence and reconciliation. */
public interface ConditionStateProvider {
    record NodeMetadata(ResourceLocation type, String trackingSignature, int stateVersion, boolean compatibilityKnown) {
        public NodeMetadata(ResourceLocation type, String trackingSignature, int stateVersion) {
            this(type,trackingSignature,stateVersion,true);
        }
    }
    default void reconcileGoal(ResourceLocation goal, Map<String,NodeMetadata> liveNodes) {}
    ConditionStateHandle open(ResourceLocation goal, String address, ResourceLocation type,
                              String trackingSignature, int stateVersion);
}
