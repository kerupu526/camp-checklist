package com.kelp.campchecklist;

import com.kelp.campchecklist.api.progress.ConditionStateHandle;
import com.kelp.campchecklist.internal.condition.ConditionStateProvider;
import com.kelp.campchecklist.internal.condition.TransactionalStateHandle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Map;
import java.util.HashSet;

/** ProgressStore-backed native node state. The class is package-private: node addresses stay internal. */
final class NativeConditionStateProvider implements ConditionStateProvider {
    private final ProgressStore.NativeStateStore store;
    private final Runnable dirty;
    private final Thread owner=Thread.currentThread();
    NativeConditionStateProvider(ProgressStore.NativeStateStore store, Runnable dirty) {
        this.store=Objects.requireNonNull(store); this.dirty=Objects.requireNonNull(dirty);
    }
    private void checkThread() {
        if (Thread.currentThread()!=owner) throw new IllegalStateException("Native condition state used off server thread");
    }
    @Override public void reconcileGoal(ResourceLocation goal, Map<String,ConditionStateProvider.NodeMetadata> liveNodes) {
        checkThread(); String goalId=goal.toString();
        for (String address : new HashSet<>(store.addresses(goalId))) {
            ConditionStateProvider.NodeMetadata metadata=liveNodes.get(address);
            ProgressStore.NativeNode stored=store.node(goalId,address);
            if (metadata==null) { store.remove(goalId,address); dirty.run(); continue; }
            // Raw type identity is known even when addon metadata cannot be prepared.
            if (!stored.type.equals(metadata.type().toString())) {
                store.remove(goalId,address); dirty.run(); continue;
            }
            // Same raw type with unknown compatibility retains the entire stored entry.
            if (!metadata.compatibilityKnown()) continue;
            if (!stored.type.equals(metadata.type().toString()) || !stored.trackingSignature.equals(metadata.trackingSignature())
                    || stored.stateVersion!=metadata.stateVersion()) {
                store.put(goalId,address,new ProgressStore.NativeNode(metadata.type().toString(),metadata.trackingSignature(),metadata.stateVersion(),new CompoundTag()));
                dirty.run();
            }
        }
    }
    @Override public ConditionStateHandle open(ResourceLocation goal, String address, ResourceLocation type,
                                                String trackingSignature, int stateVersion) {
        checkThread();
        ProgressStore.NativeNode stored=store.node(goal.toString(),address);
        boolean compatible=stored!=null && stored.type.equals(type.toString())
                && stored.trackingSignature.equals(trackingSignature) && stored.stateVersion==stateVersion;
        return new Handle(goal.toString(),address,type.toString(),trackingSignature,stateVersion,
                compatible ? stored.state : new CompoundTag(), stored!=null, !compatible && stored!=null);
    }
    private final class Handle implements TransactionalStateHandle {
        private final String goal,address,type,trackingSignature; private final int version;
        private final CompoundTag original; private CompoundTag working;
        private final boolean existed, reconciliation; private boolean active=true, committed;
        private Handle(String goal,String address,String type,String trackingSignature,int version,
                       CompoundTag initial,boolean existed,boolean reconciliation) {
            this.goal=goal; this.address=address; this.type=type; this.trackingSignature=trackingSignature; this.version=version;
            this.original=initial.copy(); this.working=initial.copy(); this.existed=existed; this.reconciliation=reconciliation;
        }
        private void check() { checkThread(); if (!active) throw new IllegalStateException("Condition state handle outlived evaluation"); }
        @Override public int stateVersion() { check(); return version; }
        @Override public CompoundTag readCopy() { check(); return working.copy(); }
        @Override public void replace(CompoundTag value) { check(); working=Objects.requireNonNull(value).copy(); }
        @Override public void clear() { check(); working=new CompoundTag(); }
        @Override public void commit() {
            check();
            boolean changed=reconciliation || !existed && !working.isEmpty() || existed && !original.equals(working);
            if (changed) {
                if (working.isEmpty() && !reconciliation) store.remove(goal,address);
                else store.put(goal,address,new ProgressStore.NativeNode(type,trackingSignature,version,working));
                dirty.run();
            }
            committed=true;
        }
        @Override public void rollback() { check(); working=original.copy(); }
        @Override public void close() { check(); active=false; }
    }
}
