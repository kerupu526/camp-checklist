package com.kelp.campchecklist.internal.condition;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Objects;

/** Internal data-only tree. Unknown type IDs survive normalization for later registry validation. */
public record ConditionNode(ResourceLocation type, JsonObject config, List<ConditionNode> children) {
    public ConditionNode {
        Objects.requireNonNull(type);
        config = Objects.requireNonNull(config).deepCopy();
        children = List.copyOf(children);
    }

    @Override public JsonObject config() { return config.deepCopy(); }
}
