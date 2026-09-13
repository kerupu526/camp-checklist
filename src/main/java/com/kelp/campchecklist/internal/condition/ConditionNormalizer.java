package com.kelp.campchecklist.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kelp.campchecklist.Definitions;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Converts both legacy goal definitions and native condition trees into one internal model. */
public final class ConditionNormalizer {
    private ConditionNormalizer() {}
    /** Shared loader/evaluation safety bounds for native condition trees. */
    public static final int MAX_DEPTH = 32;
    public static final int MAX_NODES = 1024;
    private static final Set<String> COUNT_TYPES = Set.of("item_acquired", "item_crafted", "block_placed");

    public static ConditionNode legacy(Definitions.Goal goal) {
        String type = switch (goal.type()) {
            case "manual" -> "manual";
            case "acquire_item" -> "item_acquired";
            case "craft_item", "craft_count" -> "item_crafted";
            case "place_block" -> "block_placed";
            case "advancement" -> "advancement";
            case "custom" -> "legacy_custom";
            default -> throw new IllegalArgumentException("Unknown legacy type " + goal.type());
        };
        JsonObject config = new JsonObject();
        config.addProperty("type", "camp_checklist:" + type);
        put(config, "item", goal.item());
        put(config, "tag", goal.tag());
        put(config, "block", goal.block());
        put(config, "advancement", goal.advancement());
        put(config, "checker", goal.checker());
        if (COUNT_TYPES.contains(type)) config.addProperty("count", goal.target());
        else if (type.equals("legacy_custom")) config.addProperty("target", goal.target());
        config.addProperty("unit", goal.unit());
        if (!goal.parameters().isEmpty()) config.add("parameters", goal.parameters().deepCopy());
        return nativeCondition(config);
    }

    private static void put(JsonObject object, String key, String value) {
        if (!value.isEmpty()) object.addProperty(key, value);
    }

    public static ConditionNode nativeCondition(JsonObject json) { return parse(json, 1, new Counter()); }

    private static ConditionNode parse(JsonObject json, int depth, Counter counter) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Condition tree exceeds " + MAX_DEPTH + " levels");
        if (++counter.nodes > MAX_NODES) throw new IllegalArgumentException("Condition tree exceeds " + MAX_NODES + " nodes");
        if (json == null || !json.has("type") || !json.get("type").isJsonPrimitive())
            throw new IllegalArgumentException("Condition type is required");
        ResourceLocation type;
        try { type = ResourceLocation.parse(json.get("type").getAsString()); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid condition type", e); }
        JsonObject config = json.deepCopy();
        config.remove("type");
        List<ConditionNode> children = new ArrayList<>();
        boolean core = type.getNamespace().equals("camp_checklist");
        String path = type.getPath();
        if (core && (path.equals("and") || path.equals("or"))) {
            if (json.has("child") || !json.has("children") || !json.get("children").isJsonArray()
                    || json.getAsJsonArray("children").isEmpty()) {
                throw new IllegalArgumentException("AND/OR require nonempty children");
            }
            for (JsonElement child : json.getAsJsonArray("children")) {
                if (!child.isJsonObject()) throw new IllegalArgumentException("Condition child must be an object");
                children.add(parse(child.getAsJsonObject(), depth + 1, counter));
            }
            config.remove("children");
        } else if (core && path.equals("not")) {
            if (json.has("children") || !json.has("child") || !json.get("child").isJsonObject()) {
                throw new IllegalArgumentException("NOT requires exactly one child");
            }
            children.add(parse(json.getAsJsonObject("child"), depth + 1, counter));
            config.remove("child");
        } else if (core) {
            if (COUNT_TYPES.contains(path) && !config.has("count")) config.addProperty("count", 1);
            if ((COUNT_TYPES.contains(path) || Set.of("advancement", "manual", "legacy_custom").contains(path))
                    && !config.has("unit")) config.addProperty("unit", "count");
            if (path.equals("legacy_custom") && !config.has("target")) config.addProperty("target", 1);
        }
        return new ConditionNode(type, config, children);
    }

    private static final class Counter { private int nodes; }
}
