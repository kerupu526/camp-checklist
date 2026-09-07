package com.kelp.campchecklist;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChecklistSavedData extends SavedData {
    public ProgressStore progress = new ProgressStore();
    public static ChecklistSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ChecklistSavedData::new,(tag,lookup) -> {
            ChecklistSavedData data=new ChecklistSavedData(); data.progress=ProgressStore.load(tag); return data;
        },null),"camp_checklist");
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) { return progress.save(); }
}
