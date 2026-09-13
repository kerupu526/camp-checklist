package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.api.progress.ConditionStateHandle;

/** Internal extension used to commit or roll back one evaluator transaction. */
public interface TransactionalStateHandle extends ConditionStateHandle, AutoCloseable {
    void commit();
    void rollback();
    @Override void close();
}
