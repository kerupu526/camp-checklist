package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.api.progress.ConditionStateHandle;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Phase B in-memory node state harness. No SavedData schema or numeric counter ownership. */
public final class NodeStates implements ConditionStateProvider {
    private record Stored(int version, CompoundTag value) {}
    private final Map<String, Stored> nodes = new HashMap<>();
    private final Thread owner = Thread.currentThread();
    private final Runnable dirty;
    public NodeStates(Runnable dirty) { this.dirty = Objects.requireNonNull(dirty); }
    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Node state used off its owner thread");
    }
    @Override
    public Handle open(net.minecraft.resources.ResourceLocation goal, String address,
                       net.minecraft.resources.ResourceLocation type, String trackingSignature, int version) {
        checkThread();
        String identity=goal+"|"+address;
        Stored old = nodes.get(identity);
        if (old == null || old.version() != version) {
            nodes.put(identity, new Stored(version, new CompoundTag()));
            dirty.run();
        }
        return new Handle(identity);
    }
    /** Compatibility harness entry point retained for existing internal tests. */
    public Handle open(String identity, int version) {
        return open(net.minecraft.resources.ResourceLocation.parse("camp_checklist:harness"), identity,
                net.minecraft.resources.ResourceLocation.parse("camp_checklist:harness"), identity, version);
    }
    final class Handle implements ConditionStateHandle, AutoCloseable {
        private final String identity;
        private boolean active = true;
        private Handle(String identity) { this.identity = identity; }
        private Stored stored() {
            checkThread();
            if (!active) throw new IllegalStateException("Condition state handle outlived evaluation");
            return nodes.get(identity);
        }
        public int stateVersion() { return stored().version(); }
        public CompoundTag readCopy() { return stored().value().copy(); }
        public void replace(CompoundTag value) {
            Stored old = stored();
            CompoundTag copy = Objects.requireNonNull(value).copy();
            if (!old.value().equals(copy)) { nodes.put(identity, new Stored(old.version(), copy)); dirty.run(); }
        }
        public void clear() { replace(new CompoundTag()); }
        public void close() { checkThread(); active = false; }
    }
}
