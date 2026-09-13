package com.kelp.campchecklist.internal.condition;

import com.google.gson.JsonObject;
import com.kelp.campchecklist.CheckerRegistry;
import com.kelp.campchecklist.Definitions;
import com.kelp.campchecklist.ProgressStore;
import com.kelp.campchecklist.ViewModel;
import com.kelp.campchecklist.api.progress.ConditionStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyConditionBridgeTest {
    private static Definitions.Goal goal(String raw) {
        JsonObject json=com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
        json.addProperty("tab", "test:tab");
        return Definitions.goal(ResourceLocation.parse("test:legacy_bridge"), json);
    }

    @Test void manualAndCounterGoalsBecomeReadOnlyResults() {
        var manual=goal("{\"type\":\"manual\"}");
        var manualState=new ProgressStore.Entry();
        assertEquals(ConditionStatus.UNSATISFIED, LegacyConditionBridge.evaluate(manual, manualState,
                manualState.counter(manual.signature()), null).status());
        manualState.completed=true;
        assertEquals(ConditionStatus.SATISFIED, LegacyConditionBridge.evaluate(manual, manualState,
                manualState.counter(manual.signature()), null).status());

        var count=goal("{\"type\":\"craft_count\",\"item\":\"minecraft:rail\",\"target\":10}");
        var countState=new ProgressStore.Entry();
        var counter=countState.counter(count.signature());
        counter.current=4;
        var result=LegacyConditionBridge.evaluate(count, countState, counter, null);
        assertEquals(ConditionStatus.UNSATISFIED, result.status());
        assertEquals(4, result.progress().orElseThrow().current());
        assertEquals(10, result.progress().orElseThrow().target());
    }

    @Test void customCheckerAndDetailsAreAdaptedWithoutMutatingAuthoritativeState() {
        ResourceLocation checkerId=ResourceLocation.parse("test:bridge_details");
        CheckerRegistry.register(checkerId, new CheckerRegistry.Checker() {
            public CheckerRegistry.Result evaluate(Definitions.Goal goal, net.minecraft.server.MinecraftServer server,
                                                    CompoundTag state, int version) {
                state.putInt("mutated-copy", 1);
                return new CheckerRegistry.Result(2, false, state, version);
            }
            public List<ViewModel.Detail> details(Definitions.Goal goal, CompoundTag state) {
                return List.of(new ViewModel.Detail("logic", "Logic", 2, 4, "count", "count"));
            }
        });
        var custom=goal("{\"type\":\"custom\",\"checker\":\"test:bridge_details\",\"target\":4}");
        var state=new ProgressStore.Entry();
        var counter=state.counter(custom.signature());
        counter.state.putString("authoritative", "kept");
        var result=LegacyConditionBridge.evaluate(custom, state, counter, null);
        assertEquals(ConditionStatus.UNSATISFIED, result.status());
        assertEquals("kept", counter.state.getString("authoritative"));
        assertEquals(1, result.details().size());
        assertEquals("Logic", result.details().getFirst().display().label().getString());
        assertEquals(2, result.details().getFirst().result().progress().orElseThrow().current());
        assertEquals(1, LegacyConditionBridge.viewDetails(result).size());
    }

    @Test void stickyCompletionStaysSeparateFromCurrentAutomaticProgress() {
        ResourceLocation checkerId=ResourceLocation.parse("test:bridge_regression");
        CheckerRegistry.register(checkerId, new CheckerRegistry.Checker() {
            public CheckerRegistry.Result evaluate(Definitions.Goal goal, net.minecraft.server.MinecraftServer server,
                                                    CompoundTag state, int version) {
                return new CheckerRegistry.Result(2, true, state, version);
            }
        });
        var custom=goal("{\"type\":\"custom\",\"checker\":\"test:bridge_regression\",\"target\":4}");
        var state=new ProgressStore.Entry();
        state.completed=true;
        state.toastShown=true;
        var result=LegacyConditionBridge.evaluate(custom, state, state.counter(custom.signature()), null);
        assertEquals(ConditionStatus.UNSATISFIED, result.status());
        assertEquals(2, result.progress().orElseThrow().current());
        assertTrue(state.completed);
        assertTrue(state.toastShown);
    }

    @Test void legacyAdvancementUsesHistoricalCompletionState() {
        var advancement=goal("{\"type\":\"advancement\",\"advancement\":\"minecraft:story/mine_stone\"}");
        var state=new ProgressStore.Entry();
        var counter=state.counter(advancement.signature());
        counter.current=0;
        state.completed=true;
        assertEquals(ConditionStatus.SATISFIED,
                LegacyConditionBridge.evaluate(advancement, state, counter, null).status());
    }

    @Test void missingCheckerIsUnavailableAndNativeGoalsAreRejected() {
        var missing=goal("{\"type\":\"custom\",\"checker\":\"test:missing_checker\"}");
        var state=new ProgressStore.Entry();
        state.completed=true;
        state.toastShown=true;
        var result=LegacyConditionBridge.evaluate(missing, state, state.counter(missing.signature()), null);
        assertEquals(ConditionStatus.UNAVAILABLE, result.status());
        assertTrue(result.unavailableReason().isPresent());
        assertTrue(state.completed);
        assertTrue(state.toastShown);
    }
}
