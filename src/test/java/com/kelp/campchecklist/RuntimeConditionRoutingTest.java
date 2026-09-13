package com.kelp.campchecklist;

import com.google.gson.JsonObject;
import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.condition.ConditionDisplay;
import com.kelp.campchecklist.api.condition.ConditionEvaluator;
import com.kelp.campchecklist.api.progress.ConditionResult;
import com.kelp.campchecklist.api.progress.ConditionStatus;
import com.kelp.campchecklist.internal.condition.ConditionEngine;
import com.kelp.campchecklist.internal.condition.ConditionRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** C2 integration boundary: native Definitions are routed through the runtime seam into the engine. */
class RuntimeConditionRoutingTest {
    private static final ResourceLocation GOAL = ResourceLocation.parse("test:native_runtime");

    private static Definitions.Goal nativeGoal() {
        JsonObject json = com.google.gson.JsonParser.parseString("""
                {"tab":"test:tab","title":"Native","condition":{"type":"test:satisfied"}}
                """).getAsJsonObject();
        return Definitions.goal(GOAL, json);
    }

    @Test void nativeDefinitionRoutesThroughChecklistRuntimeSeam() {
        var type = new ChecklistConditionType<JsonObject>() {
            public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
            public ConditionEvaluator<JsonObject> evaluator() { return (config, context) -> ConditionResult.booleanResult(true); }
            public ConditionDisplay display(JsonObject config) { return new ConditionDisplay(Component.literal("Fixture"), Optional.empty()); }
        };
        var registry = new ConditionRegistry();
        registry.initialize(event -> event.register(ResourceLocation.parse("test:satisfied"), type));
        var goal = nativeGoal();
        assertEquals(Definitions.SchemaKind.NATIVE, goal.schemaKind());
        assertEquals(ConditionStatus.SATISFIED,
                ChecklistRuntime.evaluateNativeCondition(goal, new ConditionEngine(registry), null, null).status());
        assertEquals(0, new ProgressStore().save().getList("goals", 10).size(),
                "native evaluation must not create legacy SavedData entries");
    }

    @Test void legacyAndNativeDefinitionsCanShareTheSameCatalogBoundary() {
        var legacy = Definitions.goal(ResourceLocation.parse("test:legacy"),
                com.google.gson.JsonParser.parseString("{\"tab\":\"test:tab\",\"type\":\"manual\"}").getAsJsonObject());
        assertEquals(Definitions.SchemaKind.LEGACY, legacy.schemaKind());
        assertThrows(IllegalArgumentException.class, () -> ChecklistRuntime.evaluateNativeCondition(
                legacy, null, null, null));
    }

    @Test void transientEvaluationFailuresRemainEligibleForRetry() {
        assertTrue(ChecklistRuntime.evaluationEligible(false),
                "a transient UNAVAILABLE result must not disable future evaluation");
        assertFalse(ChecklistRuntime.evaluationEligible(true),
                "only structural validation failure may gate evaluation");
    }

    @Test void nativeEvaluationCanRecoverAfterTransientException() {
        AtomicInteger attempts = new AtomicInteger();
        var type = new ChecklistConditionType<JsonObject>() {
            public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
            public ConditionEvaluator<JsonObject> evaluator() {
                return (config, context) -> attempts.getAndIncrement() == 0
                        ? throwTransient()
                        : ConditionResult.booleanResult(true);
            }
        };
        var registry = new ConditionRegistry();
        registry.initialize(event -> event.register(ResourceLocation.parse("test:retry"), type));
        var goal = Definitions.goal(ResourceLocation.parse("test:retry_goal"),
                com.google.gson.JsonParser.parseString("{\"tab\":\"test:tab\",\"title\":\"Retry\",\"condition\":{\"type\":\"test:retry\"}}").getAsJsonObject());
        var engine = new ConditionEngine(registry);
        assertEquals(ConditionStatus.UNAVAILABLE, ChecklistRuntime.evaluateNativeCondition(goal, engine, null, null).status());
        assertEquals(ConditionStatus.SATISFIED, ChecklistRuntime.evaluateNativeCondition(goal, engine, null, null).status());
        assertEquals(2, attempts.get());
    }

    private static ConditionResult throwTransient() {
        throw new IllegalStateException("temporary fixture failure");
    }

    @Test void nativePollingIsExcludedFromTickAndApprovedTriggersRemainExplicit() throws Exception {
        String source = Files.readString(Path.of("../..", "src/main/java/com/kelp/campchecklist/ChecklistRuntime.java"));
        int tickStart = source.indexOf("public void tick()");
        int reloadStart = source.indexOf("private void reload()", tickStart);
        assertTrue(tickStart >= 0 && reloadStart > tickStart);
        assertFalse(source.substring(tickStart, reloadStart).contains("evaluateNative"));
        assertTrue(source.contains("public void checkAdvancements(ServerPlayer p) { checkLegacyAdvancements(p); evaluateNativeGoals(); }"));
        assertTrue(source.contains("evaluateNativeGoals();"));
    }
}
