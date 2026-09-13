package com.kelp.campchecklist.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kelp.campchecklist.api.condition.*;
import com.kelp.campchecklist.api.progress.*;
import com.kelp.campchecklist.CampChecklist;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Recursive server evaluation. Each cycle shares one prepared compatibility snapshot. */
public final class ConditionEngine {
    private final ConditionRegistry registry;
    public ConditionEngine(ConditionRegistry registry) {
        if (!registry.frozen()) throw new IllegalStateException("Condition registry must be frozen before evaluation");
        this.registry=registry;
    }
    /** Diagnostic identity only: unknown configuration falls back to deterministic raw material. */
    public String signature(ConditionNode node) {
        return TrackingSignatures.signature(node,(id,config)->tracking(registry.get(id),config));
    }
    public Map<String,ConditionStateProvider.NodeMetadata> nodeMetadata(ConditionNode root) {
        return metadata(PreparedConditionNode.prepare(root,registry));
    }
    private static Map<String,ConditionStateProvider.NodeMetadata> metadata(PreparedConditionNode root) {
        Map<String,ConditionStateProvider.NodeMetadata> result=new LinkedHashMap<>();
        root.collect("root",result); return Collections.unmodifiableMap(result);
    }
    private static <C> JsonElement tracking(ChecklistConditionType<C> type,JsonObject config) {
        if (type==null) return config;
        try { return Objects.requireNonNull(type.trackingMaterial(type.codec().parse(JsonOps.INSTANCE,config).getOrThrow())); }
        catch (RuntimeException error) { return config; }
    }
    public ConditionResult evaluate(ConditionNode node,MinecraftServer server,ResourceLocation goal,ConditionStateProvider state) {
        if (server!=null && !server.isSameThread()) throw new IllegalStateException("Evaluate on the logical server thread");
        PreparedConditionNode prepared=PreparedConditionNode.prepare(node,registry);
        if (state!=null) state.reconcileGoal(goal,metadata(prepared));
        return evaluateNode(prepared,server,goal,state,"root",0);
    }
    private ConditionResult evaluateNode(PreparedConditionNode node,MinecraftServer server,ResourceLocation goal,
                                         ConditionStateProvider state,String path,int depth) {
        if (depth>=ConditionNormalizer.MAX_DEPTH) return unavailable("camp_checklist.condition.too_deep");
        if (node.failure!=null) return ConditionResult.unavailable(node.failure);
        try {
            List<ConditionDetail> details=new ArrayList<>();
            if (node.operator!=null) {
                boolean not=node.operator.equals("not");
                if (node.children.isEmpty() || not && node.children.size()!=1)
                    return unavailable("camp_checklist.condition.invalid_children");
                // Check before child evaluation so a broken negation callback cannot commit child state.
                boolean supported=!not || negationSafe(node.children.getFirst());
                for (int i=0;i<node.children.size();i++) {
                    var child=node.children.get(i);
                    var result=evaluateNode(child,server,goal,state,path+"/"+i,depth+1);
                    details.add(new ConditionDetail(Integer.toString(i),display(child),result));
                }
                if (!supported) return new ConditionResult(ConditionStatus.UNAVAILABLE,Optional.empty(),details,
                        Optional.of(Component.translatable("camp_checklist.condition.negation_unsupported")));
            } else if (!node.children.isEmpty()) return unavailable("camp_checklist.condition.invalid_children");
            return invoke(node,server,goal,state,path,details);
        } catch (RuntimeException error) {
            CampChecklist.LOGGER.warn("Checklist condition evaluation failed: type={}, goal={}",node.raw.type(),goal,error);
            return unavailable("camp_checklist.condition.evaluation_failed",node.raw.type().toString(),
                    Objects.toString(error.getMessage(),error.getClass().getSimpleName()));
        }
    }
    private static boolean negationSafe(PreparedConditionNode node) {
        if (node.failure!=null) return true; // The child retains its unavailable reason.
        if (!node.supportsNegation.getAsBoolean()) return false;
        for (var child:node.children) if (!negationSafe(child)) return false;
        return true;
    }
    private static ConditionDisplay display(PreparedConditionNode node) {
        try { if (node.display!=null) return node.display.get(); }
        catch (RuntimeException ignored) { /* Presentation failure is not a compatibility decision. */ }
        return new ConditionDisplay(Component.translatable("camp_checklist.condition.generic"),Optional.empty());
    }
    private static ConditionResult invoke(PreparedConditionNode node,MinecraftServer server,ResourceLocation goal,
                                          ConditionStateProvider states,String address,List<ConditionDetail> details) {
        int version=node.metadata.stateVersion();
        if (!node.persistent) return node.evaluator.apply(new EvaluationContext(server,goal,new NoopStateHandle(version),List.copyOf(details)));
        if (!node.metadata.compatibilityKnown()) return unavailable("camp_checklist.condition.state_backend_unavailable",goal.toString());
        if (states==null) return unavailable("camp_checklist.condition.state_backend_unavailable",goal.toString());
        var handle=states.open(goal,address,node.metadata.type(),node.metadata.trackingSignature(),version);
        try {
            ConditionResult result=node.evaluator.apply(new EvaluationContext(server,goal,handle,List.copyOf(details)));
            if (handle instanceof TransactionalStateHandle transaction) transaction.commit();
            return result;
        } catch (RuntimeException error) {
            if (handle instanceof TransactionalStateHandle transaction) transaction.rollback();
            throw error;
        } finally {
            if (handle instanceof AutoCloseable closeable) {
                try { closeable.close(); }
                catch (Exception error) { throw new IllegalStateException("Condition state close failed",error); }
            }
        }
    }
    private record EvaluationContext(MinecraftServer server,ResourceLocation goalId,ConditionStateHandle state,
                                     List<ConditionDetail> childDetails) implements BuiltinConditions.CompositeContext {}
    private static final class NoopStateHandle implements ConditionStateHandle {
        private final int version;
        private NoopStateHandle(int version) { this.version=version; }
        public int stateVersion() { return version; }
        public net.minecraft.nbt.CompoundTag readCopy() { return new net.minecraft.nbt.CompoundTag(); }
        public void replace(net.minecraft.nbt.CompoundTag value) { throw new IllegalStateException("Stateless condition attempted to replace state"); }
        public void clear() { throw new IllegalStateException("Stateless condition attempted to clear state"); }
    }
    private static ConditionResult unavailable(String key,Object... args) { return ConditionResult.unavailable(Component.translatable(key,args)); }
}
