package com.kelp.campchecklist;

import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiConsumer;

public final class ChecklistRuntime {
    private final MinecraftServer server;
    private final ChecklistSavedData data;
    private long revision=-1;
    private ChecklistLoader seenLoader;
    private boolean sync=true;
    private volatile ViewModel cachedView=ViewModel.EMPTY;
    private final Map<ResourceLocation,String> unavailable=new HashMap<>();
    private final List<PlacedCheck> pendingPlacements=new ArrayList<>();
    private record PlacedCheck(ServerLevel level,BlockPos pos,BlockState state) {}
    public ChecklistRuntime(MinecraftServer server) { this.server=server; data=ChecklistSavedData.get(server); }
    private Collection<Definitions.Goal> goals() { return CampChecklist.loader(server).catalog.goals().values(); }
    private ProgressStore.Entry entry(Definitions.Goal g) { return data.progress.entry(g.id().toString()); }
    private ProgressStore.Counter counter(Definitions.Goal g) { return entry(g).counter(g.signature()); }
    private boolean enabled(Definitions.Goal g) { return !unavailable.containsKey(g.id()); }
    /** Builds the first world snapshot as soon as the server is ready, before a UI is opened. */
    public void initialize() {
        if (seenLoader!=CampChecklist.loader(server) || revision!=CampChecklist.loader(server).revision) reload();
    }
    public void tick() {
        for (Iterator<PlacedCheck> it=pendingPlacements.iterator();it.hasNext();) {
            PlacedCheck check=it.next(); it.remove();
            if (!check.level.getBlockState(check.pos).is(check.state.getBlock())) recordPlaced(check.state);
        }
        if (seenLoader!=CampChecklist.loader(server) || revision!=CampChecklist.loader(server).revision) reload();
        int tick=server.getTickCount();
        if (tick%20==0) {
            for (Definitions.Goal g:goals()) {
                if (!g.type().equals("custom") || !enabled(g) || entry(g).completed) continue;
                CheckerRegistry.Checker c=CheckerRegistry.get(g.checker());
                int interval=Math.max(20,c.intervalTicks());
                if (c.intervalTicks()>0 && (tick/20)%((interval+19)/20)==0) evaluate(g,c);
            }
        }
        if (sync) { Network.broadcast(server,view()); sync=false; }
    }
    private void reload() {
        seenLoader=CampChecklist.loader(server); revision=seenLoader.revision; unavailable.clear();
        for (Definitions.Goal g:goals()) {
            String reason=validate(g);
            if (!reason.isEmpty()) { unavailable.put(g.id(),reason); CampChecklist.LOGGER.warn("Checklist {} unavailable: {}",g.id(),reason); }
            else {
                ProgressStore.Entry state=entry(g);
                if (!state.activeSignature.isEmpty() && !state.activeSignature.equals(g.signature())) {
                    state.completed=false;
                    state.counters.remove(g.signature());
                    data.setDirty();
                }
                state.activeSignature=g.signature();
                if (g.type().equals("craft_count") || g.type().equals("custom")) update(g,counter(g).current,false);
            }
        }
        for (ServerPlayer p:server.getPlayerList().getPlayers()) checkAdvancements(p);
        cachedView=buildView();
        sync=true;
    }
    private String validate(Definitions.Goal g) {
        if (!CampChecklist.loader(server).catalog.tabs().containsKey(g.tab())) return "Unknown tab: "+g.tab();
        if (!g.item().isEmpty() && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(g.item()))) return "Unknown item: "+g.item();
        if (!g.block().isEmpty() && !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(g.block()))) return "Unknown block: "+g.block();
        if (!g.tag().isEmpty()) {
            boolean exists=g.type().equals("place_block") ? BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK,ResourceLocation.parse(g.tag()))).filter(x -> x.size()>0).isPresent() : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,ResourceLocation.parse(g.tag()))).filter(x -> x.size()>0).isPresent();
            if (!exists) return "Missing or empty tag: "+g.tag();
        }
        if (g.type().equals("advancement") && server.getAdvancements().get(ResourceLocation.parse(g.advancement()))==null) return "Unknown advancement: "+g.advancement();
        if (g.type().equals("custom")) {
            CheckerRegistry.Checker c=CheckerRegistry.get(g.checker());
            if (c==null) return "Unknown checker: "+g.checker();
            try { return c.unavailable(g,server); } catch (Exception e) { return "Checker validation failed: "+e; }
        } return "";
    }
    private boolean matches(Definitions.Goal g,ItemStack stack) {
        if (stack.isEmpty()) return false;
        return !g.item().isEmpty() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(g.item()) : !g.tag().isEmpty() && stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(g.tag())));
    }
    public void acquire(ServerPlayer p,ItemStack stack) {
        if (p.isCreative() || p.isSpectator()) return;
        for (Definitions.Goal g:goals()) if (enabled(g) && g.type().equals("acquire_item") && matches(g,stack)) update(g,1,true);
        for (Definitions.Goal g:goals()) if (enabled(g) && g.type().equals("custom") && !entry(g).completed) {
            CheckerRegistry.Checker checker=CheckerRegistry.get(g.checker());
            if (checker == null) continue;
            try {
                ProgressStore.Counter c=counter(g);
                checker.onAcquire(g,server,p,stack,c.state,c.version);
                evaluate(g,checker);
            } catch (Exception e) {
                CampChecklist.LOGGER.warn("Acquisition checker failed for {}; it will be retried",g.id(),e);
            }
        }
    }
    public void craft(ServerPlayer p,ItemStack stack) {
        if (p.isCreative() || p.isSpectator()) return;
        for (Definitions.Goal g:goals()) if (enabled(g) && (g.type().equals("craft_item") || g.type().equals("craft_count")) && matches(g,stack)) update(g,counter(g).current+stack.getCount(),false);
        acquire(p,stack);
    }
    public void place(ServerLevel level,BlockPos pos,BlockState state) {
        pendingPlacements.add(new PlacedCheck(level,pos.immutable(),state));
    }
    private void recordPlaced(BlockState state) {
        for (Definitions.Goal g:goals()) if (enabled(g) && g.type().equals("place_block")) {
            boolean match=!g.block().isEmpty() ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(g.block()) : state.is(TagKey.create(Registries.BLOCK,ResourceLocation.parse(g.tag())));
            if (match) update(g,1,true);
        }
    }
    public void checkAdvancements(ServerPlayer p) {
        for (Definitions.Goal g:goals()) if (enabled(g) && g.type().equals("advancement")) {
            var a=server.getAdvancements().get(ResourceLocation.parse(g.advancement()));
            if (a!=null && p.getAdvancements().getOrStartProgress(a).isDone()) update(g,1,true);
        }
    }
    public void login(ServerPlayer p) { if (seenLoader!=CampChecklist.loader(server) || revision!=CampChecklist.loader(server).revision) reload(); checkAdvancements(p); Network.send(p,view()); }
    public void manual(String id,boolean completed) {
        ResourceLocation key=ResourceLocation.tryParse(id); if (key==null) return;
        Definitions.Goal g=CampChecklist.loader(server).catalog.goals().get(key);
        if (g==null || !enabled(g) || !g.type().equals("manual")) return;
        ProgressStore.Entry e=entry(g); boolean changedState=e.completed!=completed || counter(g).current!=(completed ? 1:0);
        e.completed=completed; counter(g).current=completed ? 1:0;
        if (completed && !e.toastShown) toast(g,e);
        if (changedState) changed();
    }
    private void update(Definitions.Goal g,double value,boolean complete) {
        if (!enabled(g) || !Double.isFinite(value) || value<0 || entry(g).completed) return;
        ProgressStore.Counter c=counter(g); double next=Math.max(c.current,Math.min(value,g.target()));
        boolean done=complete || next>=g.target();
        if (next==c.current && !done) return;
        c.current=next;
        if (done) { entry(g).completed=true; toast(g,entry(g)); }
        changed();
    }
    private void toast(Definitions.Goal g,ProgressStore.Entry e) {
        if (e.toastShown) return; e.toastShown=true; Network.toast(server,g.title());
    }
    private void changed() { data.setDirty(); cachedView=buildView(); sync=true; }
    public void evaluateChecker(String id) {
        if (!server.isSameThread()) throw new IllegalStateException("Checker must run on server thread");
        for (Definitions.Goal g:goals()) if (enabled(g) && g.type().equals("custom") && g.checker().equals(id) && !entry(g).completed) evaluate(g,CheckerRegistry.get(id));
    }
    /** Gives an optional integration access to its opaque per-goal state without interpreting it in core. */
    void mutateCheckerStates(String id, int stateVersion, BiConsumer<Definitions.Goal, ProgressStore.Counter> mutator) {
        boolean changed = false;
        for (Definitions.Goal g:goals()) {
            if (enabled(g) && g.type().equals("custom") && g.checker().equals(id) && !entry(g).completed) {
                ProgressStore.Counter c=counter(g);
                if (c.version != stateVersion) {
                    c.current=0;
                    c.state=new net.minecraft.nbt.CompoundTag();
                    c.version=stateVersion;
                    changed=true;
                }
                net.minecraft.nbt.CompoundTag before=c.state.copy();
                mutator.accept(g,c);
                changed |= !before.equals(c.state);
            }
        }
        if (changed) {
            data.setDirty();
            cachedView=buildView();
            sync=true;
        }
    }
    private void evaluate(Definitions.Goal g,CheckerRegistry.Checker checker) {
        try {
            ProgressStore.Counter c=counter(g);
            var result=checker.evaluate(g,server,c.state.copy(),c.version);
            if (!Double.isFinite(result.current()) || result.current()<0 || result.state()==null) throw new IllegalArgumentException("Invalid checker result");
            if (!c.state.equals(result.state()) || c.version!=result.version()) { c.state=result.state().copy(); c.version=result.version(); data.setDirty(); }
            update(g,result.current(),result.completed());
        } catch (Exception e) { sync=true; CampChecklist.LOGGER.warn("Temporary checker failure for {}; it will be retried",g.id(),e); }
    }
    public ViewModel view() {
        if (seenLoader!=CampChecklist.loader(server) || revision!=CampChecklist.loader(server).revision) reload();
        return cachedView;
    }
    private ViewModel buildView() {
        var tabs=CampChecklist.loader(server).catalog.tabs().values().stream().sorted(Comparator.comparingInt(Definitions.Tab::order).thenComparing(t -> t.id().toString())).map(t -> new ViewModel.Tab(t.id().toString(),t.title(),t.description(),t.icon(),t.order())).toList();
        var list=goals().stream().sorted(Comparator.comparingInt(Definitions.Goal::order).thenComparing(g -> g.id().toString())).map(g -> {
            boolean done=entry(g).completed;
            double current=viewCurrent(g.target(),done,counter(g).current);
            List<ViewModel.Detail> details=List.of();
            if (g.type().equals("custom")) {
                CheckerRegistry.Checker checker=CheckerRegistry.get(g.checker());
                if (checker != null) {
                    try { details=checker.details(g,counter(g).state); }
                    catch (Exception e) { CampChecklist.LOGGER.warn("Checklist detail generation failed for {}",g.id(),e); }
                }
            }
            return new ViewModel.Goal(g.id().toString(),g.tab().toString(),g.title(),g.description(),g.icon(),g.order(),done,g.type().equals("manual"),current,g.target(),done ? 1:Math.min(1,current/g.target()),g.unit(),g.displayUnit(),!enabled(g),unavailable.getOrDefault(g.id(),""),details);
        }).toList();
        return new ViewModel(tabs,list,CampChecklist.loader(server).catalog.errors().entrySet().stream().map(e -> e.getKey()+": "+e.getValue()).sorted().toList());
    }
    static double viewCurrent(double target, boolean completed, double storedCurrent) {
        return completed ? target : storedCurrent;
    }
}
