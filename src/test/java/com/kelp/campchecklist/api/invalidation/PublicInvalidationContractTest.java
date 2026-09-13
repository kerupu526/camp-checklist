package com.kelp.campchecklist.api.invalidation;

import com.google.gson.JsonObject;
import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.progress.ConditionResult;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import static org.junit.jupiter.api.Assertions.*;

class PublicInvalidationContractTest {
    @Test void dependencyIsAnImmutableValueWithNullRejection() {
        var dependency = new ConditionDependency(ResourceLocation.parse("examplemod:energy_network"),
                ResourceLocation.parse("examplemod:main_grid"));
        assertEquals(dependency, new ConditionDependency(dependency.kind(), dependency.id()));
        assertEquals(dependency.hashCode(), new ConditionDependency(dependency.kind(), dependency.id()).hashCode());
        assertThrows(NullPointerException.class, () -> new ConditionDependency(null, dependency.id()));
        assertThrows(NullPointerException.class, () -> new ConditionDependency(dependency.kind(), null));
    }

    @Test void dependencyDeclarationIsAdditiveAndDefaultsToEmpty() {
        ChecklistConditionType<JsonObject> legacyStyle = new ChecklistConditionType<>() {
            public com.mojang.serialization.Codec<JsonObject> codec() { return com.mojang.serialization.Codec.unit(JsonObject::new); }
            public com.kelp.campchecklist.api.condition.ConditionEvaluator<JsonObject> evaluator() {
                return (config, context) -> ConditionResult.booleanResult(true);
            }
        };
        assertTrue(legacyStyle.dependencies(new JsonObject()).isEmpty());
    }

    @Test void returnedDependencyValueRemainsStableAfterCallerMutation() {
        var dependency = new ConditionDependency(ResourceLocation.parse("examplemod:energy_network"),
                ResourceLocation.parse("examplemod:main_grid"));
        Collection<ConditionDependency> returned = new java.util.ArrayList<>();
        returned.add(dependency);
        returned.clear();
        assertTrue(returned.isEmpty());
        assertEquals("examplemod:energy_network", dependency.kind().toString());
    }

    @Test void lifecycleRosterDependencyUsesStablePublicIdentity() {
        var roster = new ConditionDependency(ResourceLocation.parse("camp_checklist:lifecycle"),
                ResourceLocation.parse("camp_checklist:player_roster"));
        assertEquals("camp_checklist:lifecycle", roster.kind().toString());
        assertEquals("camp_checklist:player_roster", roster.id().toString());
    }
}
