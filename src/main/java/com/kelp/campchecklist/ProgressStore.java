package com.kelp.campchecklist;

import java.util.*;
import net.minecraft.nbt.*;

/** Goal identity owns completion/toast; semantic signature owns counters and checker state. */
public final class ProgressStore {
    /** Additive storage owned by native condition nodes; legacy entries never use this area. */
    static final class NativeStateStore {
        static final int FORMAT_VERSION=1;
        private final Map<String,Map<String,NativeNode>> goals=new HashMap<>();
        private final Map<String,LegacyMarker> legacyMarkers=new HashMap<>();
        int formatVersion() { return FORMAT_VERSION; }
        NativeNode node(String goal, String address) {
            Map<String,NativeNode> bucket=goals.get(goal);
            return bucket==null ? null : bucket.get(address);
        }
        void put(String goal, String address, NativeNode node) {
            goals.computeIfAbsent(goal, x -> new HashMap<>()).put(address,node);
        }
        void remove(String goal, String address) {
            Map<String,NativeNode> bucket=goals.get(goal);
            if (bucket==null) return;
            bucket.remove(address);
            if (bucket.isEmpty()) goals.remove(goal);
        }
        int nodeCount() { return goals.values().stream().mapToInt(Map::size).sum(); }
        Set<String> addresses(String goal) { return goals.containsKey(goal) ? Set.copyOf(goals.get(goal).keySet()) : Set.of(); }
        boolean hasNode(String goal, String address) { return node(goal,address)!=null; }
        NativeNode copyNode(String goal, String address) {
            NativeNode node=node(goal,address);
            return node==null ? null : node.copy();
        }
        void marker(String goal, LegacyMarker marker) { legacyMarkers.put(goal,marker); }
        boolean putMarkerIfChanged(String goal, LegacyMarker marker) {
            LegacyMarker old=legacyMarkers.get(goal);
            if (Objects.equals(old,marker)) return false;
            legacyMarkers.put(goal,marker); return true;
        }
        LegacyMarker marker(String goal) { return legacyMarkers.get(goal); }
        int markerCount() { return legacyMarkers.size(); }
        CompoundTag save() {
            CompoundTag root=new CompoundTag(); root.putInt("formatVersion",FORMAT_VERSION);
            ListTag goalsTag=new ListTag();
            goals.forEach((goal,nodes) -> {
                CompoundTag g=new CompoundTag(); g.putString("goal",goal);
                ListTag nodeTag=new ListTag();
                nodes.forEach((address,node) -> nodeTag.add(node.save(address)));
                g.put("nodes",nodeTag); goalsTag.add(g);
            });
            root.put("goals",goalsTag);
            ListTag markers=new ListTag();
            legacyMarkers.forEach((goal,marker) -> markers.add(marker.save(goal)));
            root.put("legacyMarkers",markers);
            return root;
        }
        static NativeStateStore load(CompoundTag root) {
            NativeStateStore store=new NativeStateStore();
            for (Tag rawGoal : root.getList("goals",Tag.TAG_COMPOUND)) {
                CompoundTag g=(CompoundTag)rawGoal; String goal=g.getString("goal");
                if (goal.isBlank()) continue;
                for (Tag rawNode : g.getList("nodes",Tag.TAG_COMPOUND)) {
                    try {
                        CompoundTag n=(CompoundTag)rawNode; String address=n.getString("address");
                        if (address.isBlank()) continue;
                        store.put(goal,address,NativeNode.load(n));
                    } catch (RuntimeException ignored) { /* isolate malformed node entries */ }
                }
            }
            for (Tag rawMarker : root.getList("legacyMarkers",Tag.TAG_COMPOUND)) {
                try {
                    CompoundTag m=(CompoundTag)rawMarker; String goal=m.getString("goal");
                    if (!goal.isBlank()) store.marker(goal,LegacyMarker.load(m));
                } catch (RuntimeException ignored) { /* isolate malformed markers */ }
            }
            return store;
        }
    }
    static final class NativeNode {
        final String type, trackingSignature; final int stateVersion; final CompoundTag state;
        NativeNode(String type,String trackingSignature,int stateVersion,CompoundTag state) {
            this.type=Objects.requireNonNull(type); this.trackingSignature=Objects.requireNonNull(trackingSignature);
            this.stateVersion=stateVersion; this.state=Objects.requireNonNull(state).copy();
        }
        NativeNode copy() { return new NativeNode(type,trackingSignature,stateVersion,state); }
        CompoundTag save(String address) {
            CompoundTag n=new CompoundTag(); n.putString("address",address); n.putString("type",type);
            n.putString("trackingSignature",trackingSignature); n.putInt("stateVersion",stateVersion); n.put("state",state.copy()); return n;
        }
        static NativeNode load(CompoundTag n) {
            return new NativeNode(n.getString("type"),n.getString("trackingSignature"),n.getInt("stateVersion"),n.getCompound("state"));
        }
    }
    static final class LegacyMarker {
        final int formatVersion; final String type, legacySignature, normalizedSignature;
        LegacyMarker(int formatVersion,String type,String legacySignature,String normalizedSignature) {
            this.formatVersion=formatVersion; this.type=type; this.legacySignature=legacySignature; this.normalizedSignature=normalizedSignature;
        }
        CompoundTag save(String goal) {
            CompoundTag m=new CompoundTag(); m.putString("goal",goal); m.putInt("formatVersion",formatVersion);
            m.putString("type",type); m.putString("legacySignature",legacySignature); m.putString("normalizedSignature",normalizedSignature); return m;
        }
        static LegacyMarker load(CompoundTag m) {
            return new LegacyMarker(m.getInt("formatVersion"),m.getString("type"),m.getString("legacySignature"),m.getString("normalizedSignature"));
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof LegacyMarker marker)) return false;
            return formatVersion==marker.formatVersion && type.equals(marker.type)
                    && legacySignature.equals(marker.legacySignature) && normalizedSignature.equals(marker.normalizedSignature);
        }
        @Override public int hashCode() { return Objects.hash(formatVersion,type,legacySignature,normalizedSignature); }
    }
    public static final class Entry {
        public boolean completed, toastShown;
        public String activeSignature="";
        public final Map<String, Counter> counters = new HashMap<>();
        public Counter counter(String signature) { return counters.computeIfAbsent(signature, x -> new Counter()); }
    }
    public static final class Counter {
        public double current;
        public int version;
        public CompoundTag state = new CompoundTag();
    }
    private final Map<String, Entry> entries = new HashMap<>();
    private final NativeStateStore nativeStates = new NativeStateStore();
    public Entry entry(String id) { return entries.computeIfAbsent(id, x -> new Entry()); }
    public CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putInt("version",1);
        ListTag goals = new ListTag();
        entries.forEach((id,e) -> {
            CompoundTag g = new CompoundTag(); g.putString("id",id); g.putBoolean("completed",e.completed); g.putBoolean("toastShown",e.toastShown); g.putString("activeSignature",e.activeSignature);
            ListTag counters = new ListTag();
            e.counters.forEach((sig,c) -> { CompoundTag n = new CompoundTag(); n.putString("signature",sig); n.putDouble("current",c.current); n.putInt("version",c.version); n.put("state",c.state.copy()); counters.add(n); });
            g.put("counters",counters); goals.add(g);
        }); root.put("goals",goals); root.put("nativeFramework",nativeStates.save()); return root;
    }
    public static ProgressStore load(CompoundTag root) {
        ProgressStore store = new ProgressStore();
        for (Tag raw : root.getList("goals",Tag.TAG_COMPOUND)) {
            CompoundTag g=(CompoundTag)raw; Entry e=store.entry(g.getString("id")); e.completed=g.getBoolean("completed"); e.toastShown=g.getBoolean("toastShown"); e.activeSignature=g.getString("activeSignature");
            for (Tag cr : g.getList("counters",Tag.TAG_COMPOUND)) {
                CompoundTag n=(CompoundTag)cr; Counter c=e.counter(n.getString("signature")); double v=n.getDouble("current"); c.current=Double.isFinite(v) ? Math.max(0,v) : 0; c.version=n.getInt("version"); c.state=n.getCompound("state").copy();
            }
        }
        if (root.contains("nativeFramework",Tag.TAG_COMPOUND)) {
            NativeStateStore loaded=NativeStateStore.load(root.getCompound("nativeFramework"));
            store.nativeStates.goals.putAll(loaded.goals); store.nativeStates.legacyMarkers.putAll(loaded.legacyMarkers);
        }
        return store;
    }
    NativeStateStore nativeStates() { return nativeStates; }
}
