package com.kelp.campchecklist.internal.condition;

import com.google.gson.*;
import com.kelp.campchecklist.api.condition.*;
import com.kelp.campchecklist.api.progress.ConditionResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** One cycle's decoded configuration and compatibility authority; never persisted or exposed as API. */
final class PreparedConditionNode {
    final ConditionNode raw;
    final List<PreparedConditionNode> children;
    final ConditionStateProvider.NodeMetadata metadata;
    final boolean persistent;
    final String operator;
    final Function<ConditionEvaluationContext,ConditionResult> evaluator;
    final Supplier<ConditionDisplay> display;
    final BooleanSupplier supportsNegation;
    final Component failure;
    private final JsonObject material;

    private PreparedConditionNode(ConditionNode raw,List<PreparedConditionNode> children,PreparedType type,Component failure) {
        this.raw=raw; this.children=List.copyOf(children); this.failure=failure;
        material=new JsonObject(); material.addProperty("type",raw.type().toString());
        material.add("tracking",type==null ? raw.config() : type.tracking);
        JsonArray childMaterials=new JsonArray(); children.forEach(child->childMaterials.add(child.material));
        material.add("children",childMaterials);
        boolean known=type!=null && children.stream().allMatch(child->child.metadata.compatibilityKnown());
        metadata=new ConditionStateProvider.NodeMetadata(raw.type(),"condition-state-v1:"+TrackingSignatures.canonical(material),
                type==null ? 0 : type.version,known);
        persistent=type!=null && type.persistent;
        operator=type==null ? null : type.operator;
        evaluator=type==null ? null : type.evaluator;
        display=type==null ? null : type.display;
        supportsNegation=type==null ? null : type.supportsNegation;
    }
    static PreparedConditionNode prepare(ConditionNode node,ConditionRegistry registry) {
        List<PreparedConditionNode> children=node.children().stream().map(child->prepare(child,registry)).toList();
        var type=registry.get(node.type());
        if (type==null) return new PreparedConditionNode(node,children,null,
                Component.translatable("camp_checklist.condition.unknown",node.type().toString()));
        try { return new PreparedConditionNode(node,children,prepareType(type,node.config()),null); }
        catch (RuntimeException error) {
            return new PreparedConditionNode(node,children,null,Component.translatable("camp_checklist.condition.evaluation_failed",
                    node.type().toString(),Objects.toString(error.getMessage(),error.getClass().getSimpleName())));
        }
    }
    private static <C> PreparedType prepareType(ChecklistConditionType<C> type,JsonObject config) {
        C decoded=Objects.requireNonNull(type.codec().parse(JsonOps.INSTANCE,config).getOrThrow());
        JsonElement tracking=Objects.requireNonNull(type.trackingMaterial(decoded),"Null tracking material").deepCopy();
        int version=type.stateVersion();
        if (version<0) throw new IllegalArgumentException("Negative state version");
        boolean persistent=type.usesPersistentState(decoded);
        String operator=type instanceof BuiltinConditions.Composite composite ? composite.operator : null;
        return new PreparedType(tracking,version,persistent && operator==null,operator,
                context->Objects.requireNonNull(type.evaluator().evaluate(decoded,context)),
                ()->Objects.requireNonNull(type.display(decoded)),()->type.supportsNegation(decoded));
    }
    void collect(String address,Map<String,ConditionStateProvider.NodeMetadata> result) {
        result.put(address,metadata);
        for (int i=0;i<children.size();i++) children.get(i).collect(address+"/"+i,result);
    }
    private record PreparedType(JsonElement tracking,int version,boolean persistent,String operator,
                                Function<ConditionEvaluationContext,ConditionResult> evaluator,
                                Supplier<ConditionDisplay> display,BooleanSupplier supportsNegation) {}
}
