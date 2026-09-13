package com.kelp.campchecklist;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Server definitions; resource paths are the sole identity (no duplicate JSON id field). */
public final class Definitions {
    private Definitions() {}
    public enum SchemaKind { LEGACY, NATIVE }
    public record Tab(ResourceLocation id, String title, String description, String icon, int order) {}
    public record Goal(ResourceLocation id, ResourceLocation tab, String title, String description,
                       String icon, int order, String type, String item, String tag, String block,
                       String advancement, String checker, double target, String unit, String displayUnit,
                       JsonObject parameters, SchemaKind schemaKind,
                       com.kelp.campchecklist.internal.condition.ConditionNode condition) {
        /** Source-compatible constructor for existing legacy callers and tests. */
        public Goal(ResourceLocation id, ResourceLocation tab, String title, String description,
                    String icon, int order, String type, String item, String tag, String block,
                    String advancement, String checker, double target, String unit, String displayUnit,
                    JsonObject parameters) {
            this(id, tab, title, description, icon, order, type, item, tag, block, advancement, checker,
                    target, unit, displayUnit, parameters, SchemaKind.LEGACY, null);
        }
        /** Internal normalization seam; legacy persistence keys remain byte-for-byte compatible. */
        public com.kelp.campchecklist.internal.condition.ConditionNode normalizedCondition() {
            return schemaKind == SchemaKind.NATIVE ? condition : com.kelp.campchecklist.internal.condition.ConditionNormalizer.legacy(this);
        }
        public String signature() {
            if (schemaKind == SchemaKind.NATIVE) {
                return com.kelp.campchecklist.internal.condition.TrackingSignatures.signature(condition);
            }
            return type + "|" + unit + "|" + item + "|" + tag + "|" + block + "|" + advancement + "|" + checker + "|" + canonical(parameters);
        }
    }
    static String canonical(JsonElement e) {
        if (e.isJsonObject()) {
            TreeMap<String, String> sorted = new TreeMap<>();
            e.getAsJsonObject().entrySet().forEach(x -> sorted.put(x.getKey(), canonical(x.getValue())));
            return sorted.toString();
        }
        if (e.isJsonArray()) { List<String> values = new ArrayList<>(); e.getAsJsonArray().forEach(x -> values.add(canonical(x))); return values.toString(); }
        return e.toString();
    }
    static String str(JsonObject j, String key, String fallback) { return j.has(key) ? j.get(key).getAsString() : fallback; }
    static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    static int order(JsonObject j) { return j.has("order") ? j.get("order").getAsInt() : 0; }
    public static Tab tab(ResourceLocation id, JsonObject j) {
        return new Tab(id, str(j,"title",id.toString()),str(j,"description",""),str(j,"icon","minecraft:book"),order(j));
    }
    public static Goal goal(ResourceLocation id, JsonObject j) {
        boolean hasType = j.has("type");
        boolean hasCondition = j.has("condition");
        if (hasType == hasCondition) throw new IllegalArgumentException("Goal must define exactly one of type or condition");
        if (hasCondition) return nativeGoal(id, j);
        String type = str(j,"type","");
        if (!Set.of("manual","acquire_item","craft_item","craft_count","place_block","advancement","custom").contains(type)) throw new IllegalArgumentException("Unknown type " + type);
        String item = str(j,"item",""), tag = str(j,"tag",""), block = str(j,"block","");
        if (Set.of("acquire_item","craft_item","craft_count").contains(type) && (item.isEmpty() == tag.isEmpty())) throw new IllegalArgumentException("Exactly one item or tag required");
        if (type.equals("place_block") && (block.isEmpty() == tag.isEmpty())) throw new IllegalArgumentException("Exactly one block or tag required");
        String advancement = str(j,"advancement",""), checker = str(j,"checker","");
        if (type.equals("advancement") && advancement.isEmpty() || type.equals("custom") && checker.isEmpty()) throw new IllegalArgumentException("Missing tracking target");
        for (String s : List.of(item,tag,block,advancement,checker)) if (!s.isEmpty()) id(s);
        double target = j.has("target") ? j.get("target").getAsDouble() : 1;
        if (!Double.isFinite(target) || target <= 0 || target > 9_007_199_254_740_991d) throw new IllegalArgumentException("Invalid target");
        if (!Set.of("craft_count","custom").contains(type) && target != 1) throw new IllegalArgumentException("Boolean goal target must be 1");
        String unit = str(j,"unit","count");
        if (!Set.of("count","blocks","meters","kilometers").contains(unit)) throw new IllegalArgumentException("Unknown unit");
        String displayUnit = str(j,"display_unit",unit);
        if (!Set.of("count","blocks","meters","kilometers").contains(displayUnit)) throw new IllegalArgumentException("Unknown display_unit");
        if (unit.equals("count") != displayUnit.equals("count")) {
            throw new IllegalArgumentException("Count and distance units cannot be mixed");
        }
        return new Goal(id,id(j.get("tab").getAsString()),str(j,"title",id.toString()),str(j,"description",""),str(j,"icon","minecraft:book"),order(j),type,item,tag,block,advancement,checker,target,unit,displayUnit,j.has("parameters") ? j.getAsJsonObject("parameters").deepCopy() : new JsonObject());
    }

    private static Goal nativeGoal(ResourceLocation id, JsonObject j) {
        if (!j.get("condition").isJsonObject()) throw new IllegalArgumentException("condition must be an object");
        if (!j.has("tab") || !j.get("tab").isJsonPrimitive()) throw new IllegalArgumentException("Missing tab");
        ResourceLocation tab = id(j.get("tab").getAsString());
        var condition = com.kelp.campchecklist.internal.condition.ConditionNormalizer.nativeCondition(j.getAsJsonObject("condition"));
        return new Goal(id, tab, str(j,"title",id.toString()), str(j,"description",""),
                str(j,"icon","minecraft:book"), order(j), "", "", "", "", "", "", 1,
                "count", "count", new JsonObject(), SchemaKind.NATIVE, condition);
    }
}
