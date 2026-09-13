package com.kelp.campchecklist.internal.condition;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.TreeSet;
import java.util.Set;

/** Versioned canonical state material, separate from completion thresholds and presentation. */
public final class TrackingSignatures {
    private TrackingSignatures() {}

    /** Internal seam for the future registered type's tracking-material policy. */
    @FunctionalInterface
    public interface Material {
        JsonElement trackingMaterial(ResourceLocation type, JsonObject encodedConfig);
    }

    public static String signature(ConditionNode node) { return signature(node, TrackingSignatures::builtinMaterial); }

    public static String signature(ConditionNode node, Material material) {
        return "condition-state-v1:" + canonical(treeMaterial(node, material));
    }

    private static JsonObject treeMaterial(ConditionNode node, Material material) {
        JsonObject result = new JsonObject();
        result.addProperty("type", node.type().toString());
        result.add("tracking", material.trackingMaterial(node.type(), node.config()));
        JsonArray children = new JsonArray();
        for (ConditionNode child : node.children()) children.add(treeMaterial(child, material));
        result.add("children", children);
        return result;
    }

    private static JsonElement builtinMaterial(ResourceLocation type, JsonObject config) {
        if (type.getNamespace().equals("camp_checklist")) {
            if (Set.of("item_acquired", "item_crafted", "block_placed").contains(type.getPath())) config.remove("count");
            // Existing checker-level target is a threshold; its opaque parameters remain semantic.
            if (type.getPath().equals("legacy_custom")) config.remove("target");
        }
        return config;
    }

    public static String canonical(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet()))
                sorted.add(key, JsonParser.parseString(canonical(value.getAsJsonObject().get(key))));
            return sorted.toString();
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) array.add(JsonParser.parseString(canonical(child)));
            return array.toString();
        }
        if (value.getAsJsonPrimitive().isNumber())
            return value.getAsBigDecimal().stripTrailingZeros().toPlainString();
        return value.toString();
    }
}
