package com.kelp.campchecklist;

public final class AcquisitionHooks {
    public static final ThreadLocal<Boolean> COMMAND=ThreadLocal.withInitial(() -> false);
}
