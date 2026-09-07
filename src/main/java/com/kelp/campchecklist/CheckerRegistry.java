package com.kelp.campchecklist;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Register once during mod construction. Integrations must guard their own class loading. */
public final class CheckerRegistry {
    public interface Checker {
        /** Zero is event-only. Periodic scheduling never runs faster than once per 20 ticks. */
        default int intervalTicks() { return 0; }
        default String unavailable(Definitions.Goal goal, MinecraftServer server) { return ""; }
        /** Event hook for checkers whose source is a server-side acquisition event. */
        default void onAcquire(Definitions.Goal goal, MinecraftServer server, ServerPlayer player, ItemStack stack,
                               CompoundTag state, int version) {}
        /** Server-computed presentation details for composite goals. */
        default List<ViewModel.Detail> details(Definitions.Goal goal, CompoundTag state) { return List.of(); }
        Result evaluate(Definitions.Goal goal, MinecraftServer server, CompoundTag state, int version);
    }
    public record Result(double current, boolean completed, CompoundTag state, int version) {}
    private static final Map<ResourceLocation, Checker> CHECKERS=new HashMap<>();
    public static synchronized void register(ResourceLocation id, Checker checker) {
        if (CHECKERS.putIfAbsent(id, Objects.requireNonNull(checker)) != null) throw new IllegalArgumentException("Duplicate checker " + id);
    }
    public static Checker get(String id) { return id.isEmpty() ? null : CHECKERS.get(ResourceLocation.parse(id)); }
    /** Event integrations invoke this on the logical server thread. */
    public static void invalidate(MinecraftServer server, ResourceLocation checkerId) { CampChecklist.runtime(server).evaluateChecker(checkerId.toString()); }
}
