package com.kelp.campchecklist.api.progress;

import net.minecraft.network.chat.Component;
import java.util.*;

/** Immutable recursive evaluation result; numeric progress is optional for every status. */
public record ConditionResult(ConditionStatus status, Optional<NumericProgress> progress,
                              List<ConditionDetail> details, Optional<Component> unavailableReason,
                              Optional<Component> progressText) {
    public ConditionResult(ConditionStatus status, Optional<NumericProgress> progress,
                           List<ConditionDetail> details, Optional<Component> unavailableReason) {
        this(status, progress, details, unavailableReason, Optional.empty());
    }
    public ConditionResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(progress);
        details = List.copyOf(details);
        if (details.stream().map(ConditionDetail::key).distinct().count() != details.size())
            throw new IllegalArgumentException("Duplicate detail presentation key");
        unavailableReason = Objects.requireNonNull(unavailableReason).map(Component::copy);
        progressText = Objects.requireNonNull(progressText).map(Component::copy);
        if ((status == ConditionStatus.UNAVAILABLE) != unavailableReason.isPresent())
            throw new IllegalArgumentException("Only UNAVAILABLE requires an unavailable reason");
    }
    @Override public Optional<Component> unavailableReason() { return unavailableReason.map(Component::copy); }
    @Override public Optional<Component> progressText() { return progressText.map(Component::copy); }
    public static ConditionResult booleanResult(boolean satisfied) {
        return new ConditionResult(satisfied ? ConditionStatus.SATISFIED : ConditionStatus.UNSATISFIED,
                Optional.empty(), List.of(), Optional.empty());
    }
    public static ConditionResult unavailable(Component reason) {
        return new ConditionResult(ConditionStatus.UNAVAILABLE, Optional.empty(), List.of(), Optional.of(reason));
    }
}
