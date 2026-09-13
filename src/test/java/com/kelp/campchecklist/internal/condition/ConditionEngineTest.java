package com.kelp.campchecklist.internal.condition;

import com.google.gson.*;
import com.kelp.campchecklist.api.condition.*;
import com.kelp.campchecklist.api.event.RegisterChecklistConditionsEvent;
import com.kelp.campchecklist.api.progress.*;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.fml.event.IModBusEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionEngineTest {
    private static final ResourceLocation GOAL = ResourceLocation.parse("test:goal");
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static ConditionNode leaf(String type) { return new ConditionNode(id(type), new JsonObject(), List.of()); }
    private static ConditionNode composite(String op, ConditionNode... children) {
        return new ConditionNode(ResourceLocation.parse("camp_checklist:" + op), new JsonObject(), List.of(children));
    }
    private static ChecklistConditionType<JsonObject> constant(ConditionStatus status, boolean negatable) {
        return new ChecklistConditionType<>() {
            public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
            public boolean supportsNegation(JsonObject config) { return negatable; }
            public ConditionEvaluator<JsonObject> evaluator() {
                return (config, context) -> status == ConditionStatus.UNAVAILABLE
                        ? ConditionResult.unavailable(Component.literal("missing fixture dependency"))
                        : ConditionResult.booleanResult(status == ConditionStatus.SATISFIED);
            }
            public ConditionDisplay display(JsonObject config) { return new ConditionDisplay(Component.literal("Fixture"), Optional.empty()); }
        };
    }
    private static ConditionRegistry registry() {
        var registry = new ConditionRegistry();
        registry.initialize(event -> {
            BuiltinConditions.register(event);
            for (ConditionStatus status : ConditionStatus.values()) event.register(id(status.name().toLowerCase(Locale.ROOT)), constant(status, true));
            event.register(id("history"), constant(ConditionStatus.UNSATISFIED, false));
        });
        return registry;
    }
    private static ConditionResult evaluate(ConditionNode node) {
        return new ConditionEngine(registry()).evaluate(node, null, GOAL, new NodeStates(() -> {}));
    }
    private static ConditionNode statusNode(ConditionStatus status) { return leaf(status.name().toLowerCase(Locale.ROOT)); }

    @ParameterizedTest @EnumSource(ConditionStatus.class)
    void andThreeStateTruthTable(ConditionStatus left) {
        for (ConditionStatus right : ConditionStatus.values()) {
            var expected = left == ConditionStatus.UNSATISFIED || right == ConditionStatus.UNSATISFIED ? ConditionStatus.UNSATISFIED
                    : left == ConditionStatus.UNAVAILABLE || right == ConditionStatus.UNAVAILABLE ? ConditionStatus.UNAVAILABLE : ConditionStatus.SATISFIED;
            var result = evaluate(composite("and", statusNode(left), statusNode(right)));
            assertEquals(expected, result.status());
            assertEquals(2, result.details().size());
            assertEquals(2, result.progress().orElseThrow().target());
        }
    }
    @ParameterizedTest @EnumSource(ConditionStatus.class)
    void orThreeStateTruthTable(ConditionStatus left) {
        for (ConditionStatus right : ConditionStatus.values()) {
            var expected = left == ConditionStatus.SATISFIED || right == ConditionStatus.SATISFIED ? ConditionStatus.SATISFIED
                    : left == ConditionStatus.UNAVAILABLE || right == ConditionStatus.UNAVAILABLE ? ConditionStatus.UNAVAILABLE : ConditionStatus.UNSATISFIED;
            assertEquals(expected, evaluate(composite("or", statusNode(left), statusNode(right))).status());
        }
    }
    @ParameterizedTest @EnumSource(ConditionStatus.class)
    void notThreeStateTruthTable(ConditionStatus child) {
        var result = evaluate(composite("not", statusNode(child)));
        var expected = switch (child) {
            case SATISFIED -> ConditionStatus.UNSATISFIED;
            case UNSATISFIED -> ConditionStatus.SATISFIED;
            case UNAVAILABLE -> ConditionStatus.UNAVAILABLE;
        };
        assertEquals(expected, result.status());
        assertTrue(result.progress().isEmpty());
        if (child == ConditionStatus.UNAVAILABLE) assertEquals("missing fixture dependency", result.details().getFirst().result().unavailableReason().orElseThrow().getString());
    }
    @Test void unsupportedHistoricalNegationAndNestedHistoricalNegationAreUnavailable() {
        assertEquals(ConditionStatus.UNAVAILABLE, evaluate(composite("not", leaf("history"))).status());
        assertEquals(ConditionStatus.UNAVAILABLE, evaluate(composite("not", composite("or", leaf("history"), leaf("unsatisfied")))).status());
    }
    @Test void advancementBuiltinKeepsNegationDisabledForOnlineSnapshotSemantics() {
        var type = registry().get(ResourceLocation.parse("camp_checklist:advancement"));
        assertNotNull(type);
        @SuppressWarnings("rawtypes") ChecklistConditionType raw = (ChecklistConditionType) type;
        assertFalse(raw.supportsNegation(ResourceLocation.parse("minecraft:story/mine_diamond")));
    }
    @Test void unknownTypeIsUnavailableAndNotNeverInvertsIt() {
        assertEquals(ConditionStatus.UNAVAILABLE, evaluate(leaf("missing" )).status());
        assertEquals(ConditionStatus.UNAVAILABLE, evaluate(composite("not", leaf("missing"))).status());
        assertEquals(ConditionStatus.SATISFIED, evaluate(composite("or", leaf("missing"), leaf("satisfied"))).status());
    }
    @Test void notAdvancementIsUnavailableAndStatefulUnavailableChildDoesNotPoisonSatisfiedOr() {
        var advancement = ConditionNormalizer.nativeCondition(JsonParser.parseString(
                "{\"type\":\"camp_checklist:not\",\"child\":{\"type\":\"camp_checklist:advancement\",\"advancement\":\"minecraft:story/root\"}}"
        ).getAsJsonObject());
        assertEquals(ConditionStatus.UNAVAILABLE, evaluate(advancement).status());

        var stateful = new ChecklistConditionType<JsonObject>() {
            public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
            public boolean usesPersistentState(JsonObject config) { return true; }
            public ConditionEvaluator<JsonObject> evaluator() { return (config, context) -> ConditionResult.booleanResult(false); }
            public ConditionDisplay display(JsonObject config) { return new ConditionDisplay(Component.literal("Stateful"), Optional.empty()); }
        };
        // A separate frozen registry keeps the test's child unavailable at the engine boundary.
        var mixed = new ConditionRegistry();
        mixed.initialize(event -> {
            BuiltinConditions.register(event);
            event.register(id("stateful_child"), stateful);
            event.register(id("satisfied"), constant(ConditionStatus.SATISFIED, true));
        });
        var mixedEngine = new ConditionEngine(mixed);
        var tree = new ConditionNode(ResourceLocation.parse("camp_checklist:or"), new JsonObject(), List.of(
                leaf("stateful_child"), leaf("satisfied")));
        assertEquals(ConditionStatus.SATISFIED, mixedEngine.evaluate(tree, null, GOAL, null).status());
    }
    @Test void nestedDetailsAndPresentationKeysSurviveRepeatedEvaluation() {
        var tree = composite("and", leaf("satisfied"), composite("or", leaf("unavailable"), leaf("satisfied")));
        var first = evaluate(tree);
        var second = evaluate(tree);
        assertEquals(ConditionStatus.SATISFIED, first.status());
        assertEquals(2, first.progress().orElseThrow().current());
        assertEquals(first.details().stream().map(ConditionDetail::key).toList(), second.details().stream().map(ConditionDetail::key).toList());
        assertEquals("missing fixture dependency", first.details().get(1).result().details().getFirst().result().unavailableReason().orElseThrow().getString());
        assertEquals("Fixture", first.details().getFirst().display().label().getString());
    }
    @Test void pureCompositeEvaluationDoesNotCreatePersistentNodeState() {
        var registry = registry();
        var dirties = new AtomicInteger();
        var states = new NodeStates(dirties::incrementAndGet);
        var result = new ConditionEngine(registry).evaluate(composite("and", statusNode(ConditionStatus.SATISFIED)), null, GOAL, states);
        assertEquals(ConditionStatus.SATISFIED, result.status());
        assertEquals(0, dirties.get(), "stateless leaf and composite must not open persistent state");
    }
    @Test void statefulLeafNeedsBackendAndStatelessLeafUsesNoopState() {
        var stateful = new ChecklistConditionType<JsonObject>() {
            public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
            public boolean usesPersistentState(JsonObject config) { return true; }
            public ConditionEvaluator<JsonObject> evaluator() { return (config, context) -> ConditionResult.booleanResult(true); }
            public ConditionDisplay display(JsonObject config) { return new ConditionDisplay(Component.literal("Stateful"), Optional.empty()); }
        };
        var registry = new ConditionRegistry();
        registry.initialize(e -> e.register(id("stateful"), stateful));
        var engine = new ConditionEngine(registry);
        assertEquals(ConditionStatus.UNAVAILABLE, engine.evaluate(leaf("stateful"), null, GOAL, null).status());
        AtomicInteger dirties = new AtomicInteger();
        assertEquals(ConditionStatus.SATISFIED, engine.evaluate(leaf("stateful"), null, GOAL, new NodeStates(dirties::incrementAndGet)).status());
        assertEquals(1, dirties.get());
        assertEquals(ConditionStatus.SATISFIED, evaluate(statusNode(ConditionStatus.SATISFIED)).status());

        var mutation = new ChecklistConditionType<JsonObject>() {
            public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
            public ConditionEvaluator<JsonObject> evaluator() { return (config, context) -> {
                context.state().clear();
                return ConditionResult.booleanResult(true);
            }; }
            public ConditionDisplay display(JsonObject config) { return new ConditionDisplay(Component.literal("Mutation"), Optional.empty()); }
        };
        var mutationRegistry = new ConditionRegistry();
        mutationRegistry.initialize(e -> e.register(id("mutation"), mutation));
        assertEquals(ConditionStatus.UNAVAILABLE,
                new ConditionEngine(mutationRegistry).evaluate(leaf("mutation"), null, GOAL, null).status());
    }
    @Test void registryDuplicateAndLateRegistrationAreHardFailures() {
        var registry = new ConditionRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.initialize(e -> {
            e.register(id("same"), constant(ConditionStatus.SATISFIED, true));
            e.register(id("same"), constant(ConditionStatus.UNSATISFIED, true));
        }));
        var captured = new AtomicReference<RegisterChecklistConditionsEvent>();
        var valid = new ConditionRegistry();
        valid.initialize(captured::set);
        assertThrows(IllegalStateException.class, () -> captured.get().register(id("late"), constant(ConditionStatus.SATISFIED, true)));
        assertThrows(IllegalStateException.class, () -> valid.initialize(e -> {}));
    }
    @Test void publicRegistrationEventRoutesOnNeoForgeModBus() {
        var bus = BusBuilder.builder().markerType(IModBusEvent.class).build();
        bus.addListener((RegisterChecklistConditionsEvent event) -> event.register(id("addon"), constant(ConditionStatus.SATISFIED, true)));
        bus.addListener(BuiltinConditions::register);
        var registry = new ConditionRegistry();
        registry.initialize(bus::post);
        assertTrue(registry.frozen());
        assertEquals(5, registry.size());
        assertNotNull(registry.get(ResourceLocation.parse("camp_checklist:advancement")));
        assertEquals(ConditionStatus.SATISFIED, new ConditionEngine(registry).evaluate(leaf("addon"), null, GOAL, new NodeStates(() -> {})).status());
    }
    @Test void booleanAndNumericResultValidationDoNotConflateStatusWithRatio() {
        assertTrue(ConditionResult.booleanResult(true).progress().isEmpty());
        assertEquals(12, new NumericProgress(12, 10).current());
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new NumericProgress(invalid, 1));
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new NumericProgress(1, invalid));
        var partial = new ConditionResult(ConditionStatus.UNAVAILABLE, Optional.of(new NumericProgress(20, 10)), List.of(), Optional.of(Component.literal("missing")));
        assertEquals(20, partial.progress().orElseThrow().current());
        assertEquals(ConditionStatus.UNAVAILABLE, partial.status());
    }
    @Test void displayAndResultDefensivelyCopyMinecraftMetadata() {
        var label = Component.literal("label");
        var item = new ItemStack(Items.DIAMOND, 2);
        var display = new ConditionDisplay(label, Optional.of(item));
        label.append(" changed"); item.setCount(9);
        assertEquals("label", display.label().getString());
        assertEquals(2, display.itemIcon().orElseThrow().getCount());
        display.itemIcon().orElseThrow().setCount(20);
        assertEquals(2, display.itemIcon().orElseThrow().getCount());
        var reason = Component.literal("reason");
        var result = ConditionResult.unavailable(reason);
        reason.append(" changed");
        assertEquals("reason", result.unavailableReason().orElseThrow().getString());
        var detail = new ConditionDetail("row", display, result);
        assertThrows(IllegalArgumentException.class, () -> new ConditionResult(ConditionStatus.SATISFIED, Optional.empty(), List.of(detail, detail), Optional.empty()));
    }
    private static ChecklistConditionType<Integer> versioned(int version, int semantic) {
        return new ChecklistConditionType<>() {
            public Codec<Integer> codec() { return Codec.INT.fieldOf("target").codec(); }
            public int stateVersion() { return version; }
            public JsonElement trackingMaterial(Integer config) { return new JsonPrimitive(semantic); }
            public ConditionEvaluator<Integer> evaluator() { return (config, context) -> ConditionResult.booleanResult(false); }
        };
    }
    private static ConditionEngine versionedEngine(int version, int semantic) {
        var registry = new ConditionRegistry();
        registry.initialize(e -> e.register(id("versioned"), versioned(version, semantic)));
        return new ConditionEngine(registry);
    }
    @Test void trackingOverrideExcludesThresholdAndOpaqueVersionButIncludesSemanticRevision() {
        JsonObject config = new JsonObject(); config.addProperty("target", 10);
        var first = new ConditionNode(id("versioned"), config, List.of());
        config.addProperty("target", 20);
        var second = new ConditionNode(id("versioned"), config, List.of());
        assertEquals(versionedEngine(1, 1).signature(first), versionedEngine(2, 1).signature(second));
        assertNotEquals(versionedEngine(1, 1).signature(first), versionedEngine(1, 2).signature(first));
    }
    @Test void unknownRawConfigurationStillHasDeterministicSignature() {
        var engine = new ConditionEngine(registry());
        var first = ConditionNormalizer.nativeCondition(JsonParser.parseString("{\"type\":\"missing:addon\",\"a\":1,\"b\":2}").getAsJsonObject());
        var second = ConditionNormalizer.nativeCondition(JsonParser.parseString("{\"b\":2.0,\"a\":1,\"type\":\"missing:addon\"}").getAsJsonObject());
        assertEquals(engine.signature(first), engine.signature(second));
    }
    @Test void nodeStateVersionResetsOnlyItsOwnOpaqueDataAndHandlesCannotEscape() {
        var dirties = new AtomicInteger();
        var states = new NodeStates(dirties::incrementAndGet);
        CompoundTag input = new CompoundTag(); input.putInt("value", 4);
        ConditionStateHandle escaped;
        try (var a = states.open("a", 1); var b = states.open("b", 1)) {
            a.replace(input); b.replace(input); input.putInt("value", 9);
            a.readCopy().putInt("value", 99);
            assertEquals(4, a.readCopy().getInt("value")); escaped = a;
        }
        assertThrows(IllegalStateException.class, escaped::readCopy);
        try (var a = states.open("a", 2); var b = states.open("b", 1)) {
            assertEquals(2, a.stateVersion()); assertTrue(a.readCopy().isEmpty());
            assertEquals(4, b.readCopy().getInt("value"));
        }
        assertTrue(dirties.get() > 0);
    }
    @Test void invalidCodecIsSoftFailureAndFrozenRegistryIsRequired() {
        assertThrows(IllegalStateException.class, () -> new ConditionEngine(new ConditionRegistry()));
        assertEquals(ConditionStatus.UNAVAILABLE, versionedEngine(1, 1).evaluate(leaf("versioned"), null, GOAL, new NodeStates(() -> {})).status());
    }
    @Test void publicApiSourceDoesNotExposeInternalStorageRuntimeOrUi() throws Exception {
        Path api = Path.of("../..", "src/main/java/com/kelp/campchecklist/api");
        try (var files = Files.walk(api)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : List.of("import com.kelp.campchecklist.internal", "ProgressStore", "ChecklistRuntime", "lowdraglib", "net.minecraft.client"))
                    assertFalse(source.contains(forbidden), file + ": " + forbidden);
            }
        }
    }
}
