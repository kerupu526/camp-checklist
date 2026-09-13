package com.kelp.campchecklist.api.invalidation;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Immutable exact identity for a world-state dependency used by checklist invalidation. */
public record ConditionDependency(ResourceLocation kind, ResourceLocation id) {
    public ConditionDependency {
        Objects.requireNonNull(kind, "Dependency kind");
        Objects.requireNonNull(id, "Dependency id");
    }
}
