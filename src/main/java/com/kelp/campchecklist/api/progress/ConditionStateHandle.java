package com.kelp.campchecklist.api.progress;

import net.minecraft.nbt.CompoundTag;

/** Node-scoped, evaluation-lifetime handle. The core owns copies, versioning and dirty tracking. */
public interface ConditionStateHandle {
    int stateVersion();
    CompoundTag readCopy();
    void replace(CompoundTag value);
    void clear();
}
