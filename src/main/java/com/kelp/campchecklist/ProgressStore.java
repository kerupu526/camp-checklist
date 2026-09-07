package com.kelp.campchecklist;

import java.util.*;
import net.minecraft.nbt.*;

/** Goal identity owns completion/toast; semantic signature owns counters and checker state. */
public final class ProgressStore {
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
    public Entry entry(String id) { return entries.computeIfAbsent(id, x -> new Entry()); }
    public CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putInt("version",1);
        ListTag goals = new ListTag();
        entries.forEach((id,e) -> {
            CompoundTag g = new CompoundTag(); g.putString("id",id); g.putBoolean("completed",e.completed); g.putBoolean("toastShown",e.toastShown); g.putString("activeSignature",e.activeSignature);
            ListTag counters = new ListTag();
            e.counters.forEach((sig,c) -> { CompoundTag n = new CompoundTag(); n.putString("signature",sig); n.putDouble("current",c.current); n.putInt("version",c.version); n.put("state",c.state.copy()); counters.add(n); });
            g.put("counters",counters); goals.add(g);
        }); root.put("goals",goals); return root;
    }
    public static ProgressStore load(CompoundTag root) {
        ProgressStore store = new ProgressStore();
        for (Tag raw : root.getList("goals",Tag.TAG_COMPOUND)) {
            CompoundTag g=(CompoundTag)raw; Entry e=store.entry(g.getString("id")); e.completed=g.getBoolean("completed"); e.toastShown=g.getBoolean("toastShown"); e.activeSignature=g.getString("activeSignature");
            for (Tag cr : g.getList("counters",Tag.TAG_COMPOUND)) {
                CompoundTag n=(CompoundTag)cr; Counter c=e.counter(n.getString("signature")); double v=n.getDouble("current"); c.current=Double.isFinite(v) ? Math.max(0,v) : 0; c.version=n.getInt("version"); c.state=n.getCompound("state").copy();
            }
        } return store;
    }
}
