package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.api.invalidation.ConditionDependency;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Internal identity for a native condition dependency. It is never part of the public API. */
public record ConditionDependencyKey(String category, ResourceLocation id) implements Comparable<ConditionDependencyKey> {
    public ConditionDependencyKey {
        if (category == null || category.isBlank()) throw new IllegalArgumentException("Dependency category is required");
        Objects.requireNonNull(id, "Dependency id");
    }

    public static ConditionDependencyKey advancement(ResourceLocation id) {
        return new ConditionDependencyKey("camp_checklist:advancement", id);
    }

    public static ConditionDependencyKey playerRoster() {
        return new ConditionDependencyKey("camp_checklist:lifecycle", ResourceLocation.parse("camp_checklist:player_roster"));
    }

    public static ConditionDependencyKey fromPublic(ConditionDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        return new ConditionDependencyKey(dependency.kind().toString(), dependency.id());
    }

    public ConditionDependency toPublic() {
        return new ConditionDependency(ResourceLocation.parse(category), id);
    }

    @Override public int compareTo(ConditionDependencyKey other) {
        int categoryOrder = category.compareTo(other.category);
        return categoryOrder != 0 ? categoryOrder : id.toString().compareTo(other.id.toString());
    }
}
