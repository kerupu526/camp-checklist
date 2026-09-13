package com.kelp.campchecklist;

import com.google.gson.*;
import com.kelp.campchecklist.api.condition.*;
import com.kelp.campchecklist.api.progress.*;
import com.kelp.campchecklist.internal.condition.*;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeMetadataFailureTest {
    private static final ResourceLocation GOAL=ResourceLocation.parse("test:metadata");
    private static final ResourceLocation TYPE=ResourceLocation.parse("test:stateful");

    @ParameterizedTest
    @ValueSource(strings={"missing","decode","tracking","null","version","capability"})
    void unknownMetadataPreservesStateThroughEvaluationAndReload(String failure) {
        AtomicInteger calls=new AtomicInteger();
        boolean[] registered={false};
        ChecklistConditionType<JsonObject> type=new ChecklistConditionType<>() {
            public Codec<JsonObject> codec() {
                if (failure.equals("decode")) throw new IllegalStateException("decode fixture");
                return Codec.unit(JsonObject::new);
            }
            public JsonElement trackingMaterial(JsonObject config) {
                if (failure.equals("tracking")) throw new IllegalStateException("tracking fixture");
                return failure.equals("null") ? null : config;
            }
            public int stateVersion() {
                if (registered[0] && failure.equals("version")) throw new IllegalStateException("version fixture");
                return 2;
            }
            public boolean usesPersistentState(JsonObject config) {
                if (failure.equals("capability")) throw new IllegalStateException("capability fixture");
                return true;
            }
            public ConditionEvaluator<JsonObject> evaluator() {
                return (config,context)-> { calls.incrementAndGet(); context.state().clear(); return ConditionResult.booleanResult(true); };
            }
        };
        ConditionRegistry registry=new ConditionRegistry();
        registry.initialize(event->{ BuiltinConditions.register(event); if (!failure.equals("missing")) event.register(TYPE,type); });
        registered[0]=true;
        ConditionEngine engine=new ConditionEngine(registry);
        ConditionNode leaf=new ConditionNode(TYPE,new JsonObject(),List.of());
        ConditionNode tree=new ConditionNode(ResourceLocation.parse("camp_checklist:and"),new JsonObject(),List.of(leaf,leaf));
        ProgressStore store=seed();
        AtomicInteger dirties=new AtomicInteger();
        NativeConditionStateProvider provider=new NativeConditionStateProvider(store.nativeStates(),dirties::incrementAndGet);
        var metadata=engine.nodeMetadata(tree);
        assertFalse(metadata.get("root/0").compatibilityKnown());
        assertFalse(metadata.get("root/1").compatibilityKnown());
        provider.reconcileGoal(GOAL,metadata);
        ConditionResult result=engine.evaluate(tree,null,GOAL,provider);
        assertEquals(ConditionStatus.UNAVAILABLE,result.status());
        assertEquals(0,calls.get(),"unknown metadata must not reach evaluator");
        assertEquals(0,dirties.get());
        assertPreserved(ProgressStore.load(store.save()),"root/0",7);
        assertPreserved(ProgressStore.load(store.save()),"root/1",9);
    }

    @Test void unknownTypeChangeResetsOnlyChangedAddressAndPrunesOnlyRemovedAddress() {
        ProgressStore store=seed();
        store.nativeStates().put(GOAL.toString(),"root/removed",node(11));
        NativeConditionStateProvider provider=new NativeConditionStateProvider(store.nativeStates(),()->{});
        provider.reconcileGoal(GOAL,Map.of(
                "root/0",new ConditionStateProvider.NodeMetadata(ResourceLocation.parse("missing:different"),"fallback",0,false),
                "root/1",new ConditionStateProvider.NodeMetadata(TYPE,"fallback",0,false)));
        assertTrue(store.nativeStates().copyNode(GOAL.toString(),"root/0")==null
                || store.nativeStates().copyNode(GOAL.toString(),"root/0").state.isEmpty());
        assertFalse(store.nativeStates().hasNode(GOAL.toString(),"root/removed"));
        assertPreserved(ProgressStore.load(store.save()),"root/1",9);
    }

    private static ProgressStore seed() {
        ProgressStore store=new ProgressStore();
        store.nativeStates().put(GOAL.toString(),"root/0",node(7));
        store.nativeStates().put(GOAL.toString(),"root/1",node(9));
        return store;
    }

    @Test void evaluationPreparesEachMetadataCallbackOnce() {
        AtomicInteger decode=new AtomicInteger(),tracking=new AtomicInteger(),version=new AtomicInteger(),capability=new AtomicInteger();
        ConditionRegistry registry=new ConditionRegistry();
        registry.initialize(event->event.register(TYPE,new ChecklistConditionType<JsonObject>() {
            public Codec<JsonObject> codec() { decode.incrementAndGet(); return Codec.unit(JsonObject::new); }
            public JsonElement trackingMaterial(JsonObject config) { tracking.incrementAndGet(); return config; }
            public int stateVersion() { version.incrementAndGet(); return 1; }
            public boolean usesPersistentState(JsonObject config) { capability.incrementAndGet(); return true; }
            public ConditionEvaluator<JsonObject> evaluator() { return (config,context)->{
                CompoundTag value=new CompoundTag(); value.putInt("once",1); context.state().replace(value);
                return ConditionResult.booleanResult(true);
            }; }
        }));
        version.set(0); // Registry validates version independently of an evaluation cycle.
        ProgressStore store=new ProgressStore();
        ConditionEngine engine=new ConditionEngine(registry);
        assertEquals(ConditionStatus.SATISFIED,engine.evaluate(new ConditionNode(TYPE,new JsonObject(),List.of()),null,GOAL,
                new NativeConditionStateProvider(store.nativeStates(),()->{})).status());
        assertEquals(1,decode.get()); assertEquals(1,tracking.get()); assertEquals(1,version.get()); assertEquals(1,capability.get());
        assertEquals(1,store.nativeStates().copyNode(GOAL.toString(),"root").state.getInt("once"));
    }
    private static ProgressStore.NativeNode node(int value) {
        CompoundTag tag=new CompoundTag(); tag.putInt("opaque",value);
        return new ProgressStore.NativeNode(TYPE.toString(),"original-signature",2,tag);
    }
    private static void assertPreserved(ProgressStore store,String address,int value) {
        var node=store.nativeStates().copyNode(GOAL.toString(),address);
        assertNotNull(node); assertEquals(value,node.state.getInt("opaque"));
        assertEquals("original-signature",node.trackingSignature); assertEquals(2,node.stateVersion);
    }
}
