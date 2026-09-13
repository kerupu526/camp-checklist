package com.kelp.campchecklist.api.condition;

import com.kelp.campchecklist.api.progress.ConditionStateHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Ephemeral, server-thread-only context scoped to exactly one node in one goal. */
public interface ConditionEvaluationContext {
    MinecraftServer server();
    ResourceLocation goalId();
    ConditionStateHandle state();
}
