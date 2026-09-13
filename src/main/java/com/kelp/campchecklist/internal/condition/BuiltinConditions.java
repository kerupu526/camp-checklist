package com.kelp.campchecklist.internal.condition;

import com.google.gson.JsonObject;
import com.kelp.campchecklist.api.condition.*;
import com.kelp.campchecklist.api.event.RegisterChecklistConditionsEvent;
import com.kelp.campchecklist.api.invalidation.ConditionDependency;
import com.kelp.campchecklist.api.progress.*;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Builtins use the same type and registration contracts as addons. */
public final class BuiltinConditions {
    private BuiltinConditions() {}
    interface CompositeContext extends ConditionEvaluationContext {
        List<ConditionDetail> childDetails();
    }
    public static void register(RegisterChecklistConditionsEvent event) {
        for (String operator : List.of("and", "or", "not"))
            event.register(ResourceLocation.parse("camp_checklist:" + operator), new Composite(operator));
        event.register(ResourceLocation.parse("camp_checklist:advancement"), new Advancement());
    }

    static final class Composite implements ChecklistConditionType<JsonObject> {
        final String operator;
        Composite(String operator) { this.operator = operator; }
        public Codec<JsonObject> codec() { return Codec.unit(JsonObject::new); }
        public boolean supportsNegation(JsonObject config) { return true; }
        public ConditionDisplay display(JsonObject config) {
            return new ConditionDisplay(Component.translatable("camp_checklist.condition." + operator), Optional.empty());
        }
        public ConditionEvaluator<JsonObject> evaluator() {
            return (config, context) -> {
                List<ConditionDetail> details = ((CompositeContext) context).childDetails();
                boolean yes = details.stream().anyMatch(d -> d.result().status() == ConditionStatus.SATISFIED);
                boolean no = details.stream().anyMatch(d -> d.result().status() == ConditionStatus.UNSATISFIED);
                boolean missing = details.stream().anyMatch(d -> d.result().status() == ConditionStatus.UNAVAILABLE);
                ConditionStatus status = switch (operator) {
                    case "and" -> no ? ConditionStatus.UNSATISFIED : missing ? ConditionStatus.UNAVAILABLE : ConditionStatus.SATISFIED;
                    case "or" -> yes ? ConditionStatus.SATISFIED : missing ? ConditionStatus.UNAVAILABLE : ConditionStatus.UNSATISFIED;
                    default -> missing ? ConditionStatus.UNAVAILABLE : yes ? ConditionStatus.UNSATISFIED : ConditionStatus.SATISFIED;
                };
                Optional<NumericProgress> progress = operator.equals("not") ? Optional.empty()
                        : Optional.of(new NumericProgress(details.stream().filter(d -> d.result().status() == ConditionStatus.SATISFIED).count(), details.size()));
                Optional<Component> reason = status == ConditionStatus.UNAVAILABLE
                        ? Optional.of(Component.translatable("camp_checklist.condition.unavailable_child")) : Optional.empty();
                return new ConditionResult(status, progress, details, reason);
            };
        }
    }

    private static final class Advancement implements ChecklistConditionType<ResourceLocation> {
        public Codec<ResourceLocation> codec() { return ResourceLocation.CODEC.fieldOf("advancement").codec(); }
        public ConditionEvaluator<ResourceLocation> evaluator() {
            return (id, context) -> {
                var advancement = context.server().getAdvancements().get(id);
                if (advancement == null) return ConditionResult.unavailable(Component.translatable("camp_checklist.condition.missing_advancement", id.toString()));
                return ConditionResult.booleanResult(context.server().getPlayerList().getPlayers().stream()
                        .anyMatch(p -> p.getAdvancements().getOrStartProgress(advancement).isDone()));
            };
        }
        public Collection<ConditionDependency> dependencies(ResourceLocation id) {
            return List.of(
                    new ConditionDependency(ResourceLocation.parse("camp_checklist:advancement"), id),
                    new ConditionDependency(ResourceLocation.parse("camp_checklist:lifecycle"),
                            ResourceLocation.parse("camp_checklist:player_roster")));
        }
        public ConditionDisplay display(ResourceLocation config) {
            return new ConditionDisplay(Component.translatable("camp_checklist.condition.advancement"), Optional.empty());
        }
    }
}
