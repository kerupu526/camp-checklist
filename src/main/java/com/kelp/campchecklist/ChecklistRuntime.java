package com.kelp.campchecklist;

import com.kelp.campchecklist.api.progress.ConditionResult;
import com.kelp.campchecklist.api.progress.ConditionStatus;
import com.kelp.campchecklist.internal.condition.ConditionEngine;
import com.kelp.campchecklist.internal.condition.ConditionRegistration;
import com.kelp.campchecklist.internal.condition.ConditionStateProvider;
import com.kelp.campchecklist.internal.condition.TrackingSignatures;
import com.kelp.campchecklist.internal.condition.LegacyConditionBridge;
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
    /** Validation failures are distinct from transient legacy bridge evaluation failures. */
    private final Map<ResourceLocation,String> validationUnavailable=new HashMap<>();
    private final Map<ResourceLocation,ConditionResult> goalEvaluations=new HashMap<>();
    private ConditionEngine conditionEngine;
    private final NativeConditionStateProvider nativeStateProvider;
    private final com.kelp.campchecklist.internal.condition.ConditionInvalidationService invalidations;
    private final List<PlacedCheck> pendingPlacements=new ArrayList<>();
    private record PlacedCheck(ServerLevel level,BlockPos pos,BlockState state) {}
    public ChecklistRuntime(MinecraftServer server) {
        this.server=server; data=ChecklistSavedData.get(server);
        nativeStateProvider=new NativeConditionStateProvider(data.progress.nativeStates(),data::setDirty);
        invalidations=new com.kelp.campchecklist.internal.condition.ConditionInvalidationService();
    }
    private Collection<Definitions.Goal> goals() { return CampChecklist.loader(server).catalog.goals().values(); }
    private ProgressStore.Entry entry(Definitions.Goal g) { return data.progress.entry(g.id().toString()); }
    private ProgressStore.Counter counter(Definitions.Goal g) { return entry(g).counter(g.signature()); }
    /** Structural validation is the only reason a goal is ineligible for future evaluation. */
    private boolean enabled(Definitions.Goal g) { return evaluationEligible(validationUnavailable.containsKey(g.id())); }
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
        flushInvalidations();
        if (sync) { Network.broadcast(server,view()); sync=false; }
    }
    private void reload() {
        seenLoader=CampChecklist.loader(server); revision=seenLoader.revision; unavailable.clear(); validationUnavailable.clear(); goalEvaluations.clear();
        for (Definitions.Goal g:goals()) {
            String reason=validate(g);
            if (!reason.isEmpty()) { unavailable.put(g.id(),reason); validationUnavailable.put(g.id(),reason); CampChecklist.LOGGER.warn("Checklist {} unavailable: {}",g.id(),reason); }
        }
        rebuildNativeDependencyIndex();
        for (Definitions.Goal g:goals()) if (enabled(g)) {
            if (g.schemaKind()==Definitions.SchemaKind.NATIVE) evaluateNative(g);
            else {
                ProgressStore.Entry state=entry(g);
                if (activateSignature(state,g.signature())) data.setDirty();
                if (g.type().equals("craft_count") || g.type().equals("custom")) update(g,counter(g).current,false);
            }
        }
        for (ServerPlayer p:server.getPlayerList().getPlayers()) checkLegacyAdvancements(p);
        for (Definitions.Goal g:goals()) {
            if (g.schemaKind()==Definitions.SchemaKind.LEGACY) {
                var marker=new ProgressStore.LegacyMarker(1,g.type(),g.signature(),TrackingSignatures.signature(g.normalizedCondition()));
                if (data.progress.nativeStates().putMarkerIfChanged(g.id().toString(),marker)) data.setDirty();
            }
        }
        refreshLegacyEvaluations();
        cachedView=buildView();
        sync=true;
    }
    private String validate(Definitions.Goal g) {
        if (!CampChecklist.loader(server).catalog.tabs().containsKey(g.tab())) return "Unknown tab: "+g.tab();
        if (g.schemaKind()==Definitions.SchemaKind.NATIVE) return "";
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
    private void checkLegacyAdvancements(ServerPlayer p) {
        for (Definitions.Goal g:goals()) if (enabled(g) && g.schemaKind()==Definitions.SchemaKind.LEGACY && g.type().equals("advancement")) {
            var a=server.getAdvancements().get(ResourceLocation.parse(g.advancement()));
            if (a!=null && p.getAdvancements().getOrStartProgress(a).isDone()) update(g,1,true);
        }
    }
    /** Compatibility entry point retained for integrations; the event bridge uses the keyed overload below. */
    public void checkAdvancements(ServerPlayer p) { checkLegacyAdvancements(p); evaluateNativeGoals(); }
    public void checkAdvancement(ServerPlayer p, ResourceLocation advancement) {
        checkLegacyAdvancements(p);
        if (advancement != null) invalidations.invalidateDependency(com.kelp.campchecklist.internal.condition.ConditionDependencyKey.advancement(advancement));
    }

    /** Internal bridge target for the public exact dependency façade. */
    public void invalidatePublicDependency(com.kelp.campchecklist.internal.condition.ConditionDependencyKey dependency) {
        invalidations.invalidateDependency(dependency);
    }
    public void login(ServerPlayer p) {
        if (seenLoader!=CampChecklist.loader(server) || revision!=CampChecklist.loader(server).revision) reload();
        checkLegacyAdvancements(p);
        invalidations.invalidateDependency(com.kelp.campchecklist.internal.condition.ConditionDependencyKey.playerRoster());
        Network.send(p,view());
    }
    public void logout(ServerPlayer p) {
        invalidations.invalidateDependency(com.kelp.campchecklist.internal.condition.ConditionDependencyKey.playerRoster());
    }
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
    private void changed() { data.setDirty(); refreshLegacyEvaluations(); cachedView=buildView(); sync=true; }
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
            refreshLegacyEvaluations();
            cachedView=buildView();
            sync=true;
        }
    }
    private void evaluate(Definitions.Goal g,CheckerRegistry.Checker checker) {
        try {
            ProgressStore.Counter c=counter(g);
            var result=checker.evaluate(g,server,c.state.copy(),c.version);
            if (!Double.isFinite(result.current()) || result.current()<0 || result.state()==null) throw new IllegalArgumentException("Invalid checker result");
            boolean stateChanged=!c.state.equals(result.state()) || c.version!=result.version();
            if (stateChanged) { c.state=result.state().copy(); c.version=result.version(); data.setDirty(); }
            update(g,result.current(),result.completed());
            if (stateChanged) { refreshLegacyEvaluations(); cachedView=buildView(); sync=true; }
        } catch (Exception e) {
            refreshLegacyEvaluations();
            cachedView=buildView();
            sync=true;
            CampChecklist.LOGGER.warn("Temporary checker failure for {}; it will be retried",g.id(),e);
        }
    }

    private void evaluateNativeGoals() {
        for (Definitions.Goal g : goals()) {
            if (g.schemaKind()==Definitions.SchemaKind.NATIVE && enabled(g)) evaluateNative(g);
        }
    }

    private void rebuildNativeDependencyIndex() {
        Map<ResourceLocation, Collection<com.kelp.campchecklist.internal.condition.ConditionDependencyKey>> index = new HashMap<>();
        for (Definitions.Goal goal : goals()) if (goal.schemaKind()==Definitions.SchemaKind.NATIVE && enabled(goal)) {
            index.put(goal.id(), com.kelp.campchecklist.internal.condition.ConditionDependencyResolver.resolve(goal.normalizedCondition(), conditionRegistry()));
        }
        invalidations.rebuild(index);
    }

    private com.kelp.campchecklist.internal.condition.ConditionRegistry conditionRegistry() {
        return ConditionRegistration.REGISTRY;
    }

    /** Evaluates one indexed native goal. Missing or removed goals are an exact no-op. */
    private void evaluateNativeGoal(ResourceLocation id) {
        Definitions.Goal goal=CampChecklist.loader(server).catalog.goals().get(id);
        if (goal != null && goal.schemaKind()==Definitions.SchemaKind.NATIVE && enabled(goal)) evaluateNative(goal);
    }

    private void flushInvalidations() {
        for (ResourceLocation id : invalidations.flush()) evaluateNativeGoal(id);
    }

    /** Evaluates a native tree without changing legacy Counter storage; node persistence is C4. */
    private void evaluateNative(Definitions.Goal goal) {
        ConditionResult result;
        try {
            if (conditionEngine==null) conditionEngine=new ConditionEngine(ConditionRegistration.REGISTRY);
            result=evaluateNativeCondition(goal, conditionEngine, server, nativeStateProvider);
        } catch (Exception e) {
            result=ConditionResult.unavailable(net.minecraft.network.chat.Component.literal("Condition evaluation failed: "+e.getMessage()));
            CampChecklist.LOGGER.warn("Native checklist condition failed for {}",goal.id(),e);
        }
        ConditionResult previous=goalEvaluations.put(goal.id(),result);
        String beforeUnavailable=unavailable.get(goal.id());
        if (result.status()==ConditionStatus.UNAVAILABLE) {
            String reason=result.unavailableReason().map(net.minecraft.network.chat.Component::getString).orElse("Unavailable condition");
            unavailable.put(goal.id(),reason);
        } else {
            unavailable.remove(goal.id());
        }
        boolean completedBefore=entry(goal).completed;
        if (stickyComplete(entry(goal), result)) {
            entry(goal).completed=true;
            toast(goal,entry(goal));
            data.setDirty();
        }
        boolean stateChanged=!Objects.equals(previous,result)
                || !Objects.equals(beforeUnavailable,unavailable.get(goal.id()))
                || completedBefore!=entry(goal).completed;
        if (stateChanged) { cachedView=buildView(); sync=true; }
    }


    private void refreshLegacyEvaluations() {
        for (Definitions.Goal goal : goals()) {
            if (goal.schemaKind()!=Definitions.SchemaKind.LEGACY) continue;
            ProgressStore.Entry state=entry(goal);
            ConditionResult result;
            String validationReason=validationUnavailable.get(goal.id());
            if (validationReason!=null) {
                result=ConditionResult.unavailable(net.minecraft.network.chat.Component.literal(validationReason));
            } else {
                result=LegacyConditionBridge.evaluate(goal, state, counter(goal), server);
                if (result.status()==ConditionStatus.UNAVAILABLE) {
                    String reason=result.unavailableReason().map(net.minecraft.network.chat.Component::getString).orElse("Unavailable legacy condition");
                    unavailable.put(goal.id(),reason);
                } else {
                    unavailable.remove(goal.id());
                }
            }
            goalEvaluations.put(goal.id(), result);
        }
    }
    public ViewModel view() {
        if (seenLoader!=CampChecklist.loader(server) || revision!=CampChecklist.loader(server).revision) reload();
        return cachedView;
    }
    private ViewModel buildView() {
        var tabs=CampChecklist.loader(server).catalog.tabs().values().stream().sorted(Comparator.comparingInt(Definitions.Tab::order).thenComparing(t -> t.id().toString())).map(t -> new ViewModel.Tab(t.id().toString(),t.title(),t.description(),t.icon(),t.order())).toList();
        int[] snapshotDetailBudget={ConditionPresentation.MAX_DETAIL_NODES_PER_SNAPSHOT};
        var list=goals().stream().sorted(Comparator.comparingInt(Definitions.Goal::order).thenComparing(g -> g.id().toString())).map(g -> {
            boolean done=entry(g).completed;
            double target=g.target();
            double storedCurrent=counter(g).current;
            ConditionResult evaluation=goalEvaluations.get(g.id());
            if (evaluation!=null && evaluation.progress().isPresent()) {
                target=evaluation.progress().get().target();
                storedCurrent=evaluation.progress().get().current();
            }
            double current=viewCurrent(target,done,storedCurrent);
            List<ViewModel.Detail> details=evaluation==null ? List.of() : LegacyConditionBridge.viewDetails(evaluation);
            int cappedBudget=Math.min(ConditionPresentation.MAX_DETAIL_NODES_PER_GOAL,snapshotDetailBudget[0]);
            int[] goalDetailBudget={cappedBudget};
            ViewModel.Evaluation presentation=evaluation==null ? null : ConditionPresentation.copy(evaluation,goalDetailBudget);
            snapshotDetailBudget[0]-=cappedBudget-goalDetailBudget[0];
            return new ViewModel.Goal(g.id().toString(),g.tab().toString(),g.title(),g.description(),g.icon(),g.order(),done,g.schemaKind()==Definitions.SchemaKind.LEGACY && g.type().equals("manual"),current,target,done ? 1:Math.min(1,target<=0 ? 0:current/target),g.unit(),g.displayUnit(),!enabled(g),unavailable.getOrDefault(g.id(),""),details,presentation);
        }).toList();
        return new ViewModel(tabs,list,CampChecklist.loader(server).catalog.errors().entrySet().stream().map(e -> e.getKey()+": "+e.getValue()).sorted().toList());
    }
    static double viewCurrent(double target, boolean completed, double storedCurrent) {
        return completed ? target : storedCurrent;
    }

    /** Transient evaluation failures must not latch a goal out of later dependency-triggered retries. */
    static boolean evaluationEligible(boolean validationUnavailable) {
        return !validationUnavailable;
    }

    /** Native results are sticky: only a satisfied result can set completion. */
    static boolean stickyComplete(ProgressStore.Entry entry, ConditionResult result) {
        return result.status()==ConditionStatus.SATISFIED && !entry.completed;
    }

    /** Native routing seam used by the runtime and integration tests; null state means C2 has no backend. */
    static ConditionResult evaluateNativeCondition(Definitions.Goal goal, ConditionEngine engine,
                                                    net.minecraft.server.MinecraftServer server,
                                                    ConditionStateProvider state) {
        if (goal.schemaKind()!=Definitions.SchemaKind.NATIVE) throw new IllegalArgumentException("Only native goals use ConditionEngine routing");
        return engine.evaluate(goal.normalizedCondition(), server, goal.id(), state);
    }

    /** Reload transition, isolated so saved-state policy can be tested without a running server. */
    static boolean activateSignature(ProgressStore.Entry state, String signature) {
        if (state.activeSignature.equals(signature)) return false;
        if (!state.activeSignature.isEmpty()) {
            // Completion and toast belong to the goal ID, never to its tracking signature.
            state.counters.remove(signature);
        }
        state.activeSignature=signature;
        return true;
    }
}
