package com.kelp.campchecklist.internal.condition;

import com.google.gson.JsonElement;
import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.invalidation.ConditionDependency;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Resolves the small set of native dependency metadata understood by the core. */
public final class ConditionDependencyResolver {
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.parse("camp_checklist:advancement");

    private ConditionDependencyResolver() {}

    /** Returns deduplicated keys in deterministic order. Unknown or malformed nodes contribute no key. */
    public static Set<ConditionDependencyKey> resolve(ConditionNode root) {
        TreeSet<ConditionDependencyKey> result = new TreeSet<>();
        visit(root, result, null);
        return Collections.unmodifiableSet(result);
    }

    /** Resolves registered public declarations while retaining the legacy built-in fallback seam. */
    public static Set<ConditionDependencyKey> resolve(ConditionNode root, ConditionRegistry registry) {
        TreeSet<ConditionDependencyKey> result = new TreeSet<>();
        visit(root, result, registry);
        return Collections.unmodifiableSet(result);
    }

    private static void visit(ConditionNode node, Set<ConditionDependencyKey> result, ConditionRegistry registry) {
        if (node == null) return;
        boolean declared = false;
        if (registry != null) {
            ChecklistConditionType<?> type = registry.get(node.type());
            if (type != null) {
                declared = true;
                try {
                    Object config = type.codec().parse(JsonOps.INSTANCE, node.config()).getOrThrow();
                    Collection<ConditionDependency> dependencies = dependencies(type, config);
                    if (dependencies != null) for (ConditionDependency dependency : dependencies)
                        if (dependency != null) result.add(ConditionDependencyKey.fromPublic(dependency));
                } catch (RuntimeException ignored) {
                    // Dependency metadata is an optimization. Evaluation semantics remain unchanged.
                }
            }
        }
        if (!declared && ADVANCEMENT.equals(node.type())) {
            JsonElement value = node.config().get("advancement");
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
                if (id != null) result.add(ConditionDependencyKey.advancement(id));
            }
        }
        for (ConditionNode child : node.children()) visit(child, result, registry);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Collection<ConditionDependency> dependencies(ChecklistConditionType type, Object config) {
        return type.dependencies(config);
    }
}
