package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.CheckerRegistry;
import com.kelp.campchecklist.Definitions;
import com.kelp.campchecklist.ProgressStore;
import com.kelp.campchecklist.ViewModel;
import com.kelp.campchecklist.api.condition.ConditionDisplay;
import com.kelp.campchecklist.api.progress.ConditionDetail;
import com.kelp.campchecklist.api.progress.ConditionResult;
import com.kelp.campchecklist.api.progress.ConditionStatus;
import com.kelp.campchecklist.api.progress.NumericProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only adapter from legacy authoritative state to the neutral ConditionResult model. */
public final class LegacyConditionBridge {
    private LegacyConditionBridge() {}

    public static ConditionResult evaluate(Definitions.Goal goal, ProgressStore.Entry entry,
                                           ProgressStore.Counter counter, MinecraftServer server) {
        if (goal.schemaKind()!=Definitions.SchemaKind.LEGACY)
            throw new IllegalArgumentException("Only legacy goals use the compatibility bridge");
        return switch (goal.type()) {
            case "manual" -> entry.completed ? ConditionResult.booleanResult(true) : ConditionResult.booleanResult(false);
            case "acquire_item", "craft_item", "craft_count", "place_block" ->
                    numeric(counter.current, goal.target(), goal.unit(), goal.displayUnit());
            // Legacy advancement completion is the historical stored result; do not
            // replace it with the native online-player snapshot semantics.
            case "advancement" -> ConditionResult.booleanResult(entry.completed);
            case "custom" -> custom(goal, counter, server);
            default -> unavailable("Unknown legacy goal type: " + goal.type());
        };
    }

    private static ConditionResult custom(Definitions.Goal goal, ProgressStore.Counter counter, MinecraftServer server) {
        CheckerRegistry.Checker checker=CheckerRegistry.get(goal.checker());
        if (checker==null) return unavailable("Unknown checker: " + goal.checker());
        try {
            CheckerRegistry.Result result=checker.evaluate(goal, server, counter.state.copy(), counter.version);
            if (result==null || result.state()==null || !Double.isFinite(result.current()) || result.current()<0)
                return unavailable("Checker returned an invalid result: " + goal.checker());
            List<ConditionDetail> details=details(checker, goal, counter.state);
            return numeric(result.current(), goal.target(), goal.unit(), goal.displayUnit(), details);
        } catch (Exception e) {
            return unavailable("Checker evaluation failed: " + goal.checker() + ": " + e.getMessage());
        }
    }

    private static List<ConditionDetail> details(CheckerRegistry.Checker checker, Definitions.Goal goal,
                                                 net.minecraft.nbt.CompoundTag state) {
        try {
            List<ViewModel.Detail> legacy=checker.details(goal, state.copy());
            List<ConditionDetail> result=new ArrayList<>();
            Set<String> keys=new HashSet<>();
            for (int i=0;i<legacy.size();i++) {
                ViewModel.Detail detail=legacy.get(i);
                String key=detail.id().isEmpty() ? Integer.toString(i) : detail.id();
                if (!keys.add(key)) key=key+"#"+i;
                ConditionResult child=numeric(detail.current(), detail.target(), detail.unit(), detail.displayUnit());
                ResourceLocation item=ResourceLocation.tryParse(detail.id());
                var icon=item!=null && BuiltInRegistries.ITEM.containsKey(item)
                        ? java.util.Optional.of(new ItemStack(BuiltInRegistries.ITEM.get(item))) : java.util.Optional.<ItemStack>empty();
                Component label=detail.label().startsWith("item.") || detail.label().startsWith("block.")
                        ? Component.translatable(detail.label()) : Component.literal(detail.label());
                result.add(new ConditionDetail(key,new ConditionDisplay(label,icon),child));
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static ConditionResult numeric(double current, double target, String unit, String displayUnit) {
        return numeric(current, target, unit, displayUnit, List.of());
    }

    private static ConditionResult numeric(double current, double target, String unit, String displayUnit,
                                           List<ConditionDetail> details) {
        if (!Double.isFinite(current) || current<0 || !Double.isFinite(target) || target<=0)
            return unavailable("Invalid legacy progress values");
        NumericProgress progress=new NumericProgress(current, target);
        String suffix=switch(displayUnit) { case "kilometers" -> " km"; case "meters" -> " m"; case "blocks" -> " blocks"; default -> ""; };
        double scale=displayUnit.equals("kilometers") ? 1000d : 1d;
        Component text=Component.literal(format(current/scale)+" / "+format(target/scale)+suffix);
        return new ConditionResult(current>=target ? ConditionStatus.SATISFIED : ConditionStatus.UNSATISFIED,
                java.util.Optional.of(progress), details, java.util.Optional.empty(),java.util.Optional.of(text));
    }

    private static String format(double value) { return Math.abs(value-Math.rint(value))<0.000001 ? Long.toString(Math.round(value)) : String.format(java.util.Locale.ROOT,"%.1f",value); }

    private static ConditionResult unavailable(String reason) {
        return ConditionResult.unavailable(Component.literal(reason));
    }

    /** Presentation projection for the current flat ViewModel; recursive DTO work is C5. */
    public static List<ViewModel.Detail> viewDetails(ConditionResult result) {
        List<ViewModel.Detail> details=new ArrayList<>();
        for (ConditionDetail detail : result.details()) {
            if (detail.result().progress().isEmpty()) continue;
            NumericProgress progress=detail.result().progress().get();
            details.add(new ViewModel.Detail(detail.key(), detail.display().label().getString(),
                    progress.current(), progress.target(), "count", "count"));
        }
        return List.copyOf(details);
    }
}
