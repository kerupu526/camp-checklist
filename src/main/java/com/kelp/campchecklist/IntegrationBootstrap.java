package com.kelp.campchecklist;

import net.neoforged.fml.ModList;
import net.minecraft.server.MinecraftServer;

/** Loads optional integrations only when their owning mod is present. */
public final class IntegrationBootstrap {
    private IntegrationBootstrap() {}

    public static void register() {
        CoreCheckers.register();
        if (ModList.get().isLoaded("pneumaticcraft")) {
            PneumaticCraftIntegration.register();
        }
        if (ModList.get().isLoaded("create")) {
            CreateIntegration.register();
        }
        if (ModList.get().isLoaded("mekanism")) {
            MekanismIntegration.register();
        }
    }

    public static void tick(MinecraftServer server) {
        if (ModList.get().isLoaded("create")) {
            CreateIntegration.tick(server);
        }
    }
}
