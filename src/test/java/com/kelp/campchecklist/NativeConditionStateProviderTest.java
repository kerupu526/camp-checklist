package com.kelp.campchecklist;

import com.kelp.campchecklist.api.condition.ChecklistConditionType;
import com.kelp.campchecklist.api.condition.ConditionDisplay;
import com.kelp.campchecklist.api.progress.ConditionResult;
import com.kelp.campchecklist.api.progress.ConditionStatus;
import com.kelp.campchecklist.internal.condition.ConditionEngine;
import com.kelp.campchecklist.internal.condition.ConditionNode;
import com.kelp.campchecklist.internal.condition.ConditionRegistry;
import com.kelp.campchecklist.internal.condition.TransactionalStateHandle;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeConditionStateProviderTest {
    private static final ResourceLocation GOAL=ResourceLocation.parse("test:native_state");
    private static final ResourceLocation TYPE=ResourceLocation.parse("test:stateful");

    @Test void readOnlyEvaluationIsLazyAndReplaceRoundTripsWithoutLegacyPollution() {
        ProgressStore store=new ProgressStore(); AtomicInteger dirties=new AtomicInteger();
        NativeConditionStateProvider provider=new NativeConditionStateProvider(store.nativeStates(),dirties::incrementAndGet);
        var handle=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-a",1);
        assertTrue(handle.readCopy().isEmpty());
        handle.commit(); handle.close();
        assertEquals(0,store.nativeStates().nodeCount());
        assertEquals(0,dirties.get());

        handle=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-a",1);
        CompoundTag value=new CompoundTag(); value.putInt("count",7); handle.replace(value); handle.commit(); handle.close();
        assertEquals(1,store.nativeStates().nodeCount());
        assertEquals(0,store.entry(GOAL.toString()).counters.size());
        ProgressStore reloaded=ProgressStore.load(store.save());
        assertEquals(7,reloaded.nativeStates().copyNode(GOAL.toString(),"root").state.getInt("count"));
    }

    @Test void rollbackLeavesNoPartialStateAndClearRemovesExistingNode() {
        ProgressStore store=new ProgressStore(); AtomicInteger dirties=new AtomicInteger();
        NativeConditionStateProvider provider=new NativeConditionStateProvider(store.nativeStates(),dirties::incrementAndGet);
        var handle=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-a",1);
        CompoundTag value=new CompoundTag(); value.putInt("count",3); handle.replace(value); handle.commit(); handle.close();
        int before=dirties.get();
        handle=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-a",1);
        handle.replace(new CompoundTag()); handle.rollback(); handle.close();
        assertEquals(3,store.nativeStates().copyNode(GOAL.toString(),"root").state.getInt("count"));
        assertEquals(before,dirties.get());
        handle=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-a",1);
        handle.clear(); handle.commit(); handle.close();
        assertFalse(store.nativeStates().hasNode(GOAL.toString(),"root"));
    }

    @Test void metadataMismatchResetsOnlyThatNodeAndMissingStateRemainsPreserved() {
        ProgressStore store=new ProgressStore(); NativeConditionStateProvider provider=
                new NativeConditionStateProvider(store.nativeStates(),()->{});
        var a=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-a",1);
        CompoundTag first=new CompoundTag(); first.putInt("count",9); a.replace(first); a.commit(); a.close();
        var sibling=(TransactionalStateHandle)provider.open(GOAL,"root/1",TYPE,"sig-a",1);
        sibling.replace(first); sibling.commit(); sibling.close();
        var changed=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig-b",2);
        assertTrue(changed.readCopy().isEmpty()); changed.commit(); changed.close();
        assertEquals(0,store.nativeStates().copyNode(GOAL.toString(),"root").state.getInt("count"));
        assertEquals(9,store.nativeStates().copyNode(GOAL.toString(),"root/1").state.getInt("count"));

        ProgressStore roundTrip=ProgressStore.load(store.save());
        assertTrue(roundTrip.nativeStates().hasNode(GOAL.toString(),"root"));
        assertTrue(roundTrip.nativeStates().hasNode(GOAL.toString(),"root/1"));
    }

    @Test void unknownOrInvalidMetadataPreservesOpaqueState() {
        ProgressStore store=new ProgressStore(); NativeConditionStateProvider provider=
                new NativeConditionStateProvider(store.nativeStates(),()->{});
        var unknownType=ResourceLocation.parse("test:missing_addon");
        var handle=(TransactionalStateHandle)provider.open(GOAL,"root",unknownType,"addon-signature",4);
        CompoundTag value=new CompoundTag(); value.putInt("kept",11); handle.replace(value); handle.commit(); handle.close();
        ConditionRegistry registry=new ConditionRegistry(); registry.initialize(event -> {});
        var node=new ConditionNode(unknownType,new com.google.gson.JsonObject(),java.util.List.of());
        var metadata=new ConditionEngine(registry).nodeMetadata(node).get("root");
        assertFalse(metadata.compatibilityKnown());
        provider.reconcileGoal(GOAL,java.util.Map.of("root",metadata));
        assertEquals(11,store.nativeStates().copyNode(GOAL.toString(),"root").state.getInt("kept"));

        ResourceLocation failingType=ResourceLocation.parse("test:tracking_failure");
        var failing=new ChecklistConditionType<com.google.gson.JsonObject>() {
            public Codec<com.google.gson.JsonObject> codec() { return Codec.unit(com.google.gson.JsonObject::new); }
            public com.kelp.campchecklist.api.condition.ConditionEvaluator<com.google.gson.JsonObject> evaluator() {
                return (config,context) -> ConditionResult.booleanResult(true);
            }
            public com.google.gson.JsonElement trackingMaterial(com.google.gson.JsonObject config) { throw new IllegalStateException("fixture"); }
        };
        ConditionRegistry failingRegistry=new ConditionRegistry(); failingRegistry.initialize(event -> event.register(failingType,failing));
        var failingHandle=(TransactionalStateHandle)provider.open(GOAL,"tracking",failingType,"stored",1);
        failingHandle.replace(value); failingHandle.commit(); failingHandle.close();
        var failingMeta=new ConditionEngine(failingRegistry).nodeMetadata(new ConditionNode(failingType,new com.google.gson.JsonObject(),java.util.List.of())).get("root");
        assertFalse(failingMeta.compatibilityKnown());
        provider.reconcileGoal(GOAL,java.util.Map.of("tracking",failingMeta));
        assertEquals(11,store.nativeStates().copyNode(GOAL.toString(),"tracking").state.getInt("kept"));
    }

    @Test void legacyStorageAndNormalizationMarkerUseSeparateAdditiveArea() {
        ProgressStore store=new ProgressStore();
        assertEquals(1,store.nativeStates().formatVersion());
        store.entry("test:legacy").completed=true;
        store.entry("test:legacy").counter("legacy-sig").current=4;
        assertTrue(store.nativeStates().putMarkerIfChanged("test:legacy",
                new ProgressStore.LegacyMarker(1,"craft_count","legacy-sig","condition-state-v1:x")));
        assertFalse(store.nativeStates().putMarkerIfChanged("test:legacy",
                new ProgressStore.LegacyMarker(1,"craft_count","legacy-sig","condition-state-v1:x")));
        ProgressStore loaded=ProgressStore.load(store.save());
        assertTrue(loaded.entry("test:legacy").completed);
        assertEquals(4,loaded.entry("test:legacy").counter("legacy-sig").current);
        assertEquals(1,loaded.nativeStates().markerCount());
    }

    @Test void statefulEngineCommitsAndRecoversOpaqueState() {
        ConditionRegistry registry=new ConditionRegistry();
        ChecklistConditionType<CompoundTag> type=new ChecklistConditionType<>() {
            public Codec<CompoundTag> codec() { return Codec.unit(CompoundTag::new); }
            public com.kelp.campchecklist.api.condition.ConditionEvaluator<CompoundTag> evaluator() {
                return (config,context) -> {
                    CompoundTag state=context.state().readCopy(); int count=state.getInt("count")+1;
                    state.putInt("count",count); context.state().replace(state);
                    return ConditionResult.booleanResult(count>=2);
                };
            }
            public boolean usesPersistentState(CompoundTag config) { return true; }
            public int stateVersion() { return 1; }
            public ConditionDisplay display(CompoundTag config) { return new ConditionDisplay(Component.literal("Stateful"), Optional.empty()); }
        };
        registry.initialize(event -> event.register(TYPE,type));
        ConditionEngine engine=new ConditionEngine(registry);
        ProgressStore store=new ProgressStore(); NativeConditionStateProvider provider=
                new NativeConditionStateProvider(store.nativeStates(),()->{});
        var goal=Definitions.goal(GOAL,com.google.gson.JsonParser.parseString(
                "{\"tab\":\"test:tab\",\"condition\":{\"type\":\"test:stateful\"}}").getAsJsonObject());
        assertEquals(ConditionStatus.UNSATISFIED,ChecklistRuntime.evaluateNativeCondition(goal,engine,null,provider).status());
        assertEquals(ConditionStatus.SATISFIED,ChecklistRuntime.evaluateNativeCondition(goal,engine,null,provider).status());
        assertEquals(2,store.nativeStates().copyNode(GOAL.toString(),"root").state.getInt("count"));
    }

    @Test void evaluatorExceptionRollsBackPartialMutation() {
        ConditionRegistry registry=new ConditionRegistry();
        ChecklistConditionType<CompoundTag> type=new ChecklistConditionType<>() {
            public Codec<CompoundTag> codec() { return Codec.unit(CompoundTag::new); }
            public com.kelp.campchecklist.api.condition.ConditionEvaluator<CompoundTag> evaluator() {
                return (config,context) -> {
                    CompoundTag state=new CompoundTag(); state.putInt("partial",1); context.state().replace(state);
                    throw new IllegalStateException("fixture failure");
                };
            }
            public boolean usesPersistentState(CompoundTag config) { return true; }
            public ConditionDisplay display(CompoundTag config) { return new ConditionDisplay(Component.literal("Throwing"), Optional.empty()); }
        };
        registry.initialize(event -> event.register(ResourceLocation.parse("test:throwing"),type));
        ProgressStore store=new ProgressStore(); NativeConditionStateProvider provider=
                new NativeConditionStateProvider(store.nativeStates(),()->{});
        var goal=Definitions.goal(GOAL,com.google.gson.JsonParser.parseString(
                "{\"tab\":\"test:tab\",\"condition\":{\"type\":\"test:throwing\"}}").getAsJsonObject());
        ConditionResult result=ChecklistRuntime.evaluateNativeCondition(goal,new ConditionEngine(registry),null,provider);
        assertEquals(ConditionStatus.UNAVAILABLE,result.status());
        assertFalse(store.nativeStates().hasNode(GOAL.toString(),"root"));
    }

    @Test void offThreadStateAccessIsRejected() throws Exception {
        ProgressStore store=new ProgressStore(); NativeConditionStateProvider provider=
                new NativeConditionStateProvider(store.nativeStates(),()->{});
        var handle=(TransactionalStateHandle)provider.open(GOAL,"root",TYPE,"sig",1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread other=new Thread(() -> { try { handle.readCopy(); } catch (Throwable error) { failure.set(error); } });
        other.start(); other.join(); handle.rollback(); handle.close();
        assertInstanceOf(IllegalStateException.class,failure.get());
    }
}
