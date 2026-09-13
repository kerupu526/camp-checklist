package com.kelp.campchecklist;

import com.google.gson.*;
import com.kelp.campchecklist.internal.condition.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionNormalizationTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static Definitions.Goal legacy(String value) {
        JsonObject config = json(value);
        config.addProperty("tab", "test:tab");
        return Definitions.goal(ResourceLocation.parse("test:goal"), config);
    }
    private static ConditionNode node(String value) { return ConditionNormalizer.nativeCondition(json(value)); }
    private static String signature(String value) { return TrackingSignatures.signature(node(value)); }

    @Test void allSevenLegacyTypesHaveExplicitMappings() {
        var inputs = List.of(
                "{\"type\":\"manual\"}",
                "{\"type\":\"acquire_item\",\"item\":\"minecraft:diamond\"}",
                "{\"type\":\"craft_item\",\"tag\":\"c:ingots\"}",
                "{\"type\":\"craft_count\",\"item\":\"minecraft:rail\",\"target\":128}",
                "{\"type\":\"place_block\",\"block\":\"minecraft:stone\"}",
                "{\"type\":\"advancement\",\"advancement\":\"minecraft:adventure/trade\"}",
                "{\"type\":\"custom\",\"checker\":\"addon:check\",\"target\":7,\"parameters\":{\"x\":2}}");
        var expected = List.of("manual", "item_acquired", "item_crafted", "item_crafted", "block_placed", "advancement", "legacy_custom");
        for (int i = 0; i < inputs.size(); i++) {
            var normalized = legacy(inputs.get(i)).normalizedCondition();
            assertEquals("camp_checklist:" + expected.get(i), normalized.type().toString());
            assertTrue(normalized.children().isEmpty());
        }
        assertEquals(128, legacy(inputs.get(3)).normalizedCondition().config().get("count").getAsInt());
        assertEquals("addon:check", legacy(inputs.get(6)).normalizedCondition().config().get("checker").getAsString());
        assertEquals(2, legacy(inputs.get(6)).normalizedCondition().config().getAsJsonObject("parameters").get("x").getAsInt());
    }

    @Test void exactLegacyAndNativeNormalizationMatchesWithoutMigrationHeuristics() {
        var old = legacy("{\"type\":\"acquire_item\",\"item\":\"minecraft:diamond\"}").normalizedCondition();
        var nativeNode = node("{\"type\":\"camp_checklist:item_acquired\",\"item\":\"minecraft:diamond\"}");
        assertEquals(old, nativeNode);
        assertEquals(TrackingSignatures.signature(old), TrackingSignatures.signature(nativeNode));
    }

    @Test void thresholdsAndCosmeticsKeepTrackingButMatcherChangesDoNot() {
        var old = legacy("{\"type\":\"craft_count\",\"item\":\"minecraft:rail\",\"target\":64}");
        var cosmetic = legacy("{\"type\":\"craft_count\",\"item\":\"minecraft:rail\",\"target\":128,\"title\":\"New\",\"description\":\"Changed\",\"order\":9,\"icon\":\"minecraft:stick\"}");
        assertEquals(old.signature(), cosmetic.signature());
        assertEquals(TrackingSignatures.signature(old.normalizedCondition()), TrackingSignatures.signature(cosmetic.normalizedCondition()));
        assertEquals(signature("{\"type\":\"camp_checklist:item_acquired\",\"item\":\"minecraft:diamond\",\"count\":64}"),
                signature("{\"count\":128,\"item\":\"minecraft:diamond\",\"type\":\"camp_checklist:item_acquired\"}"));
        assertNotEquals(signature("{\"type\":\"camp_checklist:item_acquired\",\"item\":\"minecraft:diamond\"}"),
                signature("{\"type\":\"camp_checklist:item_acquired\",\"item\":\"minecraft:coal\"}"));
    }

    @Test void unknownAddonConfigIsRetainedAndSignaturePolicyCanOverrideFullConfigDefault() {
        var first = node("{\"type\":\"addon:area\",\"target\":10,\"radius\":8}");
        var second = node("{\"type\":\"addon:area\",\"target\":20,\"radius\":8}");
        assertNotEquals(TrackingSignatures.signature(first), TrackingSignatures.signature(second));
        TrackingSignatures.Material policy = (type, config) -> { config.remove("target"); return config; };
        assertEquals(TrackingSignatures.signature(first, policy), TrackingSignatures.signature(second, policy));
        assertEquals(10, first.config().get("target").getAsInt());
        assertNotEquals(TrackingSignatures.signature(first, policy), TrackingSignatures.signature(node("{\"type\":\"addon:area\",\"target\":10,\"radius\":9}"), policy));
    }

    @Test void nestedTreeKeepsOperatorsChildOrderAndUnknownNodes() {
        var tree = node("{\"type\":\"camp_checklist:and\",\"children\":[{\"type\":\"addon:unknown\"},{\"type\":\"camp_checklist:not\",\"child\":{\"type\":\"addon:snapshot\"}}]}");
        assertEquals(2, tree.children().size());
        assertEquals("addon:unknown", tree.children().getFirst().type().toString());
        assertEquals(1, tree.children().get(1).children().size());
        assertNotEquals(TrackingSignatures.signature(tree), TrackingSignatures.signature(new ConditionNode(tree.type(), tree.config(), tree.children().reversed())));
        assertNotEquals(TrackingSignatures.signature(tree), TrackingSignatures.signature(new ConditionNode(ResourceLocation.parse("camp_checklist:or"), tree.config(), tree.children())));
    }

    @Test void malformedCompositeStructureIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> node("{\"type\":\"camp_checklist:and\",\"children\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> node("{\"type\":\"camp_checklist:not\",\"children\":[]}"));
    }

    @Test void nativeGoalUsesTheSameNormalizedConditionBoundary() {
        var goal = Definitions.goal(ResourceLocation.parse("example:goal"), json("""
                {"tab":"example:progression","title":"Example","description":"Details",
                 "condition":{"type":"missing:mana_level","amount":1000}}
                """));
        assertEquals(Definitions.SchemaKind.NATIVE, goal.schemaKind());
        assertEquals("missing:mana_level", goal.normalizedCondition().type().toString());
        assertEquals(1000, goal.normalizedCondition().config().get("amount").getAsInt());
        assertEquals(goal.signature(), TrackingSignatures.signature(goal.normalizedCondition()));

        var cosmetic = Definitions.goal(ResourceLocation.parse("example:goal"), json("""
                {"tab":"example:progression","title":"Renamed","description":"Changed","order":7,
                 "icon":"minecraft:stick","condition":{"type":"missing:mana_level","amount":1000}}
                """));
        assertEquals(goal.signature(), cosmetic.signature());
        var changed = Definitions.goal(ResourceLocation.parse("example:goal"), json("""
                {"tab":"example:progression","condition":{"type":"missing:mana_level","amount":2000}}
                """));
        assertNotEquals(goal.signature(), changed.signature());
    }

    @Test void nativeAndLegacyAdvancementGoalsHaveEqualSemanticMaterial() {
        var legacyGoal = legacy("{\"type\":\"advancement\",\"advancement\":\"minecraft:story/mine_diamond\"}");
        var nativeGoal = Definitions.goal(ResourceLocation.parse("example:native"), json("""
                {"tab":"example:progression",
                 "condition":{"type":"camp_checklist:advancement","advancement":"minecraft:story/mine_diamond"}}
                """));
        assertEquals(legacyGoal.normalizedCondition(), nativeGoal.normalizedCondition());
        assertEquals(TrackingSignatures.signature(legacyGoal.normalizedCondition()), nativeGoal.signature());
    }

    @Test void nativeAndLegacySchemaAreMutuallyExclusive() {
        assertThrows(IllegalArgumentException.class, () -> Definitions.goal(ResourceLocation.parse("test:bad"),
                json("{\"tab\":\"test:tab\",\"type\":\"manual\",\"condition\":{\"type\":\"missing:test\"}}")));
        assertThrows(IllegalArgumentException.class, () -> Definitions.goal(ResourceLocation.parse("test:bad"),
                json("{\"tab\":\"test:tab\"}")));
        assertThrows(IllegalArgumentException.class, () -> Definitions.goal(ResourceLocation.parse("test:bad"),
                json("{\"tab\":\"test:tab\",\"condition\":\"not-an-object\"}")));
    }

    @Test void nativeTreeAppliesSharedDepthAndNodeBounds() {
        JsonObject atDepthLimit = json("{\"type\":\"missing:leaf\"}");
        for (int i = 0; i < ConditionNormalizer.MAX_DEPTH - 1; i++) {
            JsonObject parent = new JsonObject();
            parent.addProperty("type", "camp_checklist:not");
            parent.add("child", atDepthLimit);
            atDepthLimit = parent;
        }
        JsonObject depthBoundary = atDepthLimit;
        assertDoesNotThrow(() -> ConditionNormalizer.nativeCondition(depthBoundary));

        JsonObject deep = json("{\"type\":\"missing:leaf\"}");
        for (int i = 0; i < ConditionNormalizer.MAX_DEPTH; i++) {
            JsonObject parent = new JsonObject();
            parent.addProperty("type", "camp_checklist:not");
            parent.add("child", deep);
            deep = parent;
        }
        JsonObject tooDeep = new JsonObject();
        tooDeep.addProperty("type", "camp_checklist:not");
        tooDeep.add("child", deep);
        assertThrows(IllegalArgumentException.class, () -> ConditionNormalizer.nativeCondition(tooDeep));

        JsonObject atNodeLimit = new JsonObject();
        atNodeLimit.addProperty("type", "camp_checklist:and");
        JsonArray allowedChildren = new JsonArray();
        for (int i = 0; i < ConditionNormalizer.MAX_NODES - 1; i++) allowedChildren.add(json("{\"type\":\"missing:leaf\"}"));
        atNodeLimit.add("children", allowedChildren);
        assertDoesNotThrow(() -> ConditionNormalizer.nativeCondition(atNodeLimit));

        JsonObject tooMany = new JsonObject();
        tooMany.addProperty("type", "camp_checklist:and");
        JsonArray children = new JsonArray();
        for (int i = 0; i < ConditionNormalizer.MAX_NODES; i++) children.add(json("{\"type\":\"missing:leaf\"}"));
        tooMany.add("children", children);
        assertThrows(IllegalArgumentException.class, () -> ConditionNormalizer.nativeCondition(tooMany));
    }

    @Test void everyMalformedCompositeShapeIsRejected() {
        var malformed = List.of(
                "{\"type\":\"camp_checklist:and\"}",
                "{\"type\":\"camp_checklist:and\",\"children\":{}}",
                "{\"type\":\"camp_checklist:or\"}",
                "{\"type\":\"camp_checklist:or\",\"children\":[]}",
                "{\"type\":\"camp_checklist:not\"}",
                "{\"type\":\"camp_checklist:not\",\"child\":{\"type\":\"missing:a\"},\"children\":[]}",
                "{\"type\":\"camp_checklist:not\",\"children\":[{\"type\":\"missing:a\"},{\"type\":\"missing:b\"}]}"
        );
        for (String raw : malformed) assertThrows(IllegalArgumentException.class, () -> node(raw));
        assertThrows(IllegalArgumentException.class, () -> node("{\"type\":\"not a resource location\"}"));
        assertThrows(IllegalArgumentException.class, () -> node("{\"foo\":\"missing:type\"}"));
    }

    @Test void immutableConfigAndCanonicalNumbersPreserveStableStateMaterial() {
        JsonObject input = json("{\"type\":\"addon:test\",\"a\":1,\"b\":2}");
        var tree = ConditionNormalizer.nativeCondition(input);
        input.addProperty("a", 99);
        tree.config().addProperty("a", 100);
        assertEquals(1, tree.config().get("a").getAsInt());
        assertThrows(UnsupportedOperationException.class, () -> tree.children().add(tree));
        assertEquals(TrackingSignatures.signature(tree), signature("{\"b\":2.0,\"a\":1e0,\"type\":\"addon:test\"}"));
    }

    @Test void actualCampCatalogKeepsFourTabsFifteenGoalsAndAllLegacyStorageKeys() throws Exception {
        Path data = Path.of("../..", "src/main/resources/data/camp_checklist/checklist");
        Set<String> tabs = new HashSet<>();
        try (var files = Files.list(data.resolve("tabs"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList())
                tabs.add("camp_checklist:" + path.getFileName().toString().replace(".json", ""));
        }
        assertEquals(4, tabs.size());
        int count = 0;
        try (var files = Files.walk(data.resolve("goals"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject raw = json(Files.readString(path));
                var goal = Definitions.goal(ResourceLocation.parse("test:goal"), raw);
                assertTrue(tabs.contains(goal.tab().toString()));
                String before = goal.signature();
                assertNotNull(goal.normalizedCondition());
                assertEquals(before, goal.signature());
                assertEquals(goal.type()+"|"+goal.unit()+"|"+goal.item()+"|"+goal.tag()+"|"+goal.block()+"|"+goal.advancement()+"|"+goal.checker()+"|"+Definitions.canonical(goal.parameters()), before);
                var store = new ProgressStore();
                var entry = store.entry(goal.id().toString());
                entry.counter(before).current = 3;
                entry.counter(before).version = 5;
                entry.counter(before).state.putString("addon:data", "opaque");
                var loaded = ProgressStore.load(store.save()).entry(goal.id().toString()).counter(goal.signature());
                assertEquals(3, loaded.current);
                assertEquals(5, loaded.version);
                assertEquals("opaque", loaded.state.getString("addon:data"));
                count++;
            }
        }
        assertEquals(15, count);
    }
}
