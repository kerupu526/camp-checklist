package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.CampChecklist;
import com.kelp.campchecklist.api.invalidation.ConditionDependency;
import net.minecraft.server.MinecraftServer;

/** Internal bridge kept out of the addon-facing API surface. */
public final class ConditionInvalidationBridge {
    private ConditionInvalidationBridge() {}

    public static void invalidate(MinecraftServer server, ConditionDependency dependency) {
        if (!CampChecklist.ready(server)) {
            throw new IllegalStateException("Checklist runtime is not active for the supplied server");
        }
        CampChecklist.runtime(server).invalidatePublicDependency(ConditionDependencyKey.fromPublic(dependency));
    }
}
