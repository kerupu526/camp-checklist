package com.kelp.campchecklist.api.progress;

import com.kelp.campchecklist.api.condition.ConditionDisplay;
import java.util.Objects;

/** Key is presentation identity within this sibling list, never an external command address. */
public record ConditionDetail(String key, ConditionDisplay display, ConditionResult result) {
    public ConditionDetail {
        if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Detail key is empty");
        Objects.requireNonNull(display);
        Objects.requireNonNull(result);
    }
}
