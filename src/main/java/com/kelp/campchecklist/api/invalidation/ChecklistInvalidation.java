package com.kelp.campchecklist.api.invalidation;

import net.minecraft.server.MinecraftServer;
import java.util.Objects;

/**
 * Addon-facing exact dependency invalidation façade.
 * Calls enqueue a reevaluation and never evaluate immediately; the core scheduler owns flushing.
 */
public final class ChecklistInvalidation {
    private ChecklistInvalidation() {}

    /**
     * Enqueues native goals indexed by {@code dependency} for the next scheduler flush.
     * The server must be explicit and the call must run on that server's logical thread.
     * A valid runtime with no matching dependency is a normal no-op.
     */
    public static void invalidate(MinecraftServer server, ConditionDependency dependency) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dependency, "dependency");
        com.kelp.campchecklist.internal.condition.ConditionInvalidationBridge.invalidate(server, dependency);
    }
}
