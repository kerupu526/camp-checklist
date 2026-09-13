package com.kelp.campchecklist;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;

@Mod(CampChecklist.ID)
public final class CampChecklist {
    public static final String ID="camp_checklist";
    public static final Logger LOGGER=LogUtils.getLogger();
    private static final Map<MinecraftServer,ChecklistRuntime> RUNTIMES=new IdentityHashMap<>();
    private static final Map<MinecraftServer,ChecklistLoader> LOADERS=new IdentityHashMap<>();
    private static ChecklistLoader fallbackLoader=new ChecklistLoader();
    public CampChecklist(IEventBus bus) {
        com.kelp.campchecklist.internal.condition.ConditionRegistration.install(bus);
        IntegrationBootstrap.register();
        ChecklistUi.registerMenu();
        bus.addListener(Network::register);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            // A fresh listener is installed for every resource reload.
            var loader=new ChecklistLoader();
            var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server!=null) LOADERS.put(server,loader); else fallbackLoader=loader;
            e.addListener(loader);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            runtime(e.getServer()).initialize();
            ChecklistUi.prewarmServer(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> { ChecklistUi.tickServer(e.getServer()); IntegrationBootstrap.tick(e.getServer()); runtime(e.getServer()).tick(); });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            ChecklistUi.clearServer(e.getServer());
            RUNTIMES.remove(e.getServer());
            LOADERS.remove(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> { if (e.getEntity() instanceof ServerPlayer p) runtime(p.server).login(p); });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (e.getEntity() instanceof ServerPlayer p) runtime(p.server).logout(p); });
        NeoForge.EVENT_BUS.addListener((AdvancementEvent.AdvancementEarnEvent e) -> { if (e.getEntity() instanceof ServerPlayer p) runtime(p.server).checkAdvancement(p,e.getAdvancement().id()); });
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p && e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) runtime(p.server).place(level,e.getPos(),e.getPlacedBlock()); });
    }
    public static ChecklistRuntime runtime(MinecraftServer server) { return RUNTIMES.computeIfAbsent(server,ChecklistRuntime::new); }
    public static ChecklistLoader loader(MinecraftServer server) { return LOADERS.getOrDefault(server,fallbackLoader); }
    public static boolean ready(MinecraftServer server) { return RUNTIMES.containsKey(server); }
}
