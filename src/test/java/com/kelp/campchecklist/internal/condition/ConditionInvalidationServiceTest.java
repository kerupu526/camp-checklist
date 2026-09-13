package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.condition.ConditionEvaluator;
import com.kelp.campchecklist.api.invalidation.ConditionDependency;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ConditionInvalidationServiceTest {
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    @Test void exactDependencyInvalidatesOnlyIndexedGoalsInStableOrder() {
        var service = new ConditionInvalidationService();
        var advancement = ConditionDependencyKey.advancement(id("minecraft:story/root"));
        service.replaceGoalDependencies(id("test:z"), List.of(advancement));
        service.replaceGoalDependencies(id("test:a"), List.of(advancement));
        service.replaceGoalDependencies(id("test:unrelated"), List.of(ConditionDependencyKey.playerRoster()));

        service.invalidateDependency(advancement);
        assertEquals(List.of(id("test:a"), id("test:z")), service.flush());
        assertEquals(List.of(), service.flush());
    }

    @Test void sameTickStormCoalescesAndReentrantInvalidationIsNextBatch() {
        var service = new ConditionInvalidationService();
        var first = id("test:first");
        var second = id("test:second");
        service.replaceGoalDependencies(first, List.of());
        service.replaceGoalDependencies(second, List.of());
        service.invalidateGoal(first);
        service.invalidateGoal(first);
        var batch = service.flush();
        assertEquals(List.of(first), batch);
        service.invalidateGoal(second);
        assertEquals(List.of(second), service.flush());
    }

    @Test void removedGoalAndUnknownDependencyAreNoOps() {
        var service = new ConditionInvalidationService();
        var goal = id("test:goal");
        service.replaceGoalDependencies(goal, List.of(ConditionDependencyKey.playerRoster()));
        service.removeGoal(goal);
        service.invalidateDependency(ConditionDependencyKey.playerRoster());
        service.invalidateDependency(ConditionDependencyKey.advancement(id("minecraft:missing")));
        assertTrue(service.flush().isEmpty());
    }

    @Test void resolverAggregatesCompositeAdvancementsAndIgnoresMalformedNodes() {
        var root = ConditionNormalizer.nativeCondition(com.google.gson.JsonParser.parseString(
                "{\"type\":\"camp_checklist:and\",\"children\":[" +
                        "{\"type\":\"camp_checklist:advancement\",\"advancement\":\"minecraft:z\"}," +
                        "{\"type\":\"camp_checklist:advancement\",\"advancement\":\"minecraft:a\"}," +
                        "{\"type\":\"camp_checklist:advancement\",\"advancement\":\"minecraft:z\"}," +
                        "{\"type\":\"missing:unknown\",\"advancement\":\"not an id\"}" +
                        "]}").getAsJsonObject());
        assertEquals(List.of(
                ConditionDependencyKey.advancement(id("minecraft:a")),
                ConditionDependencyKey.advancement(id("minecraft:z"))),
                new ArrayList<>(ConditionDependencyResolver.resolve(root)));
    }

    @Test void offThreadCallsAreRejected() throws Exception {
        var service = new ConditionInvalidationService();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { service.invalidateAllNative(InvalidationReason.INTERNAL); }
            catch (Throwable error) { failure.set(error); }
        });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    @Test void resolverUsesPublicDeclarationWithRegisteredType() {
        var registry = new ConditionRegistry();
        var typeId = id("example:test_condition");
        var declared = new ConditionDependency(id("example:energy_network"), id("example:main_grid"));
        registry.initialize(event -> event.register(typeId, new ChecklistConditionType<com.google.gson.JsonObject>() {
            public com.mojang.serialization.Codec<com.google.gson.JsonObject> codec() { return com.mojang.serialization.Codec.unit(com.google.gson.JsonObject::new); }
            public ConditionEvaluator<com.google.gson.JsonObject> evaluator() { return (config, context) -> com.kelp.campchecklist.api.progress.ConditionResult.booleanResult(true); }
            public java.util.Collection<ConditionDependency> dependencies(com.google.gson.JsonObject config) { return java.util.List.of(declared, declared); }
        }));
        var root = ConditionNormalizer.nativeCondition(com.google.gson.JsonParser.parseString(
                "{\"type\":\"example:test_condition\"}").getAsJsonObject());
        assertEquals(Set.of(ConditionDependencyKey.fromPublic(declared)), ConditionDependencyResolver.resolve(root, registry));
    }

    @Test void publicDeclarationPropagatesExactAdvancementAndRosterDependencies() {
        var registry = new ConditionRegistry();
        var typeId = id("example:advancement_like");
        var advancement = new ConditionDependency(id("camp_checklist:advancement"), id("minecraft:story/root"));
        var roster = new ConditionDependency(id("camp_checklist:lifecycle"), id("camp_checklist:player_roster"));
        registry.initialize(event -> event.register(typeId, new ChecklistConditionType<com.google.gson.JsonObject>() {
            public com.mojang.serialization.Codec<com.google.gson.JsonObject> codec() { return com.mojang.serialization.Codec.unit(com.google.gson.JsonObject::new); }
            public ConditionEvaluator<com.google.gson.JsonObject> evaluator() { return (config, context) -> com.kelp.campchecklist.api.progress.ConditionResult.booleanResult(true); }
            public java.util.Collection<ConditionDependency> dependencies(com.google.gson.JsonObject config) { return java.util.List.of(advancement, roster); }
        }));
        var root = ConditionNormalizer.nativeCondition(com.google.gson.JsonParser.parseString(
                "{\"type\":\"camp_checklist:and\",\"children\":[{\"type\":\"example:advancement_like\"}]}" ).getAsJsonObject());
        assertEquals(Set.of(ConditionDependencyKey.fromPublic(advancement), ConditionDependencyKey.playerRoster()),
                ConditionDependencyResolver.resolve(root, registry));
    }

    @Test void builtinAdvancementDeclaresExactAdvancementAndRosterDependencies() {
        var registry = new ConditionRegistry();
        registry.initialize(BuiltinConditions::register);
        var root = ConditionNormalizer.nativeCondition(com.google.gson.JsonParser.parseString(
                "{\"type\":\"camp_checklist:advancement\",\"advancement\":\"minecraft:story/root\"}").getAsJsonObject());
        assertEquals(Set.of(ConditionDependencyKey.advancement(id("minecraft:story/root")), ConditionDependencyKey.playerRoster()),
                ConditionDependencyResolver.resolve(root, registry));
    }
}
