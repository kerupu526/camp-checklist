package com.kelp.campchecklist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=CampChecklist.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientModel {
    private static volatile ViewModel snapshot=ViewModel.EMPTY;
    private static final KeyMapping OPEN_KEY=new KeyMapping("key.camp_checklist.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.camp_checklist");
    private static final ClientReloadOpenGate OPEN_GATE=new ClientReloadOpenGate();
    private static final UiRetryPolicy.Latest<ViewModel> PENDING_SNAPSHOT=new UiRetryPolicy.Latest<>();
    private static boolean openAfterSnapshot;
    public static ViewModel snapshot() { return snapshot; }
    public static void clear() { snapshot=ViewModel.EMPTY; OPEN_GATE.reset(); PENDING_SNAPSHOT.clear(); openAfterSnapshot=false; }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        Network.clientReceiver=m -> {
            if (m.kind().equals("toast")) Minecraft.getInstance().getToasts().addToast(SystemToast.multiline(Minecraft.getInstance(),SystemToast.SystemToastId.PERIODIC_NOTIFICATION,Component.translatable("camp_checklist.toast.title"),Component.literal(m.text())));
        };
        Network.snapshotReceiver=ClientModel::applySnapshot;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            boolean openRequested=OPEN_GATE.finishClientTick(Minecraft.getInstance().getOverlay() == null);
            boolean snapshotReady=applyPendingSnapshot();
            while (OPEN_KEY.consumeClick()) openRequested |= OPEN_GATE.requestOpen();
            if (openRequested || openAfterSnapshot) {
                if (snapshotReady) {
                    openAfterSnapshot=false;
                    Network.requestOpen();
                } else {
                    openAfterSnapshot=true;
                }
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> { clear(); ChecklistUi.clearClientInstances(); });
    }
    @SubscribeEvent public static void registerReloadListeners(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((barrier, manager, preparationProfiler, applicationProfiler, backgroundExecutor, gameExecutor) -> {
            long generation=OPEN_GATE.beginReload();
            return barrier.wait(Unit.INSTANCE).thenRunAsync(() -> OPEN_GATE.markReloadComplete(generation), gameExecutor);
        });
    }
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent e) { e.register(OPEN_KEY); }
    static void applySnapshot(ViewModel model) {
        long generation=OPEN_GATE.generation();
        if (OPEN_GATE.isBlocked()) {
            PENDING_SNAPSHOT.hold(model,generation);
            return;
        }
        if (PENDING_SNAPSHOT.hasPending(generation)) {
            PENDING_SNAPSHOT.replace(model,generation);
            return;
        }
        attemptSnapshot(model,generation);
    }

    private static boolean applyPendingSnapshot() {
        if (OPEN_GATE.isBlocked()) return false;
        long generation=OPEN_GATE.generation();
        ViewModel model=PENDING_SNAPSHOT.takeRetry(generation);
        if (model == null) model=PENDING_SNAPSHOT.takeWhenReady(generation);
        if (model == null) return !PENDING_SNAPSHOT.hasPending(generation);
        attemptSnapshot(model,generation);
        return !PENDING_SNAPSHOT.hasPending(generation);
    }

    private static void attemptSnapshot(ViewModel model,long generation) {
        try {
            ChecklistUi.setClientModel(model);
            ChecklistUi.prewarmClient(model);
            snapshot=model;
            PENDING_SNAPSHOT.clear();
        } catch (Throwable failure) {
            if (!UiRetryPolicy.isTransientFontFailure(failure)) rethrow(failure);
            PENDING_SNAPSHOT.hold(model,generation);
            if (PENDING_SNAPSHOT.scheduleRetry()) {
                CampChecklist.LOGGER.warn("Checklist client snapshot hit the transient LDLib2 font race; retrying once on the next client tick");
            } else {
                PENDING_SNAPSHOT.clear();
                CampChecklist.LOGGER.error("Checklist client snapshot failed after the bounded LDLib2 font-race retry", failure);
            }
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }
}
