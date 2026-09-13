package com.kelp.campchecklist.api.condition;

import com.kelp.campchecklist.api.progress.ConditionResult;

/** Synchronous logical-server evaluation. Do not retain the context or its state handle. */
@FunctionalInterface
public interface ConditionEvaluator<C> {
    ConditionResult evaluate(C config, ConditionEvaluationContext context);
}
