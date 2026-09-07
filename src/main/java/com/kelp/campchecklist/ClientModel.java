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
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=CampChecklist.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientModel {
    private static volatile ViewModel snapshot=ViewModel.EMPTY;
    private static final KeyMapping OPEN_KEY=new KeyMapping("key.camp_checklist.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.camp_checklist");
    public static ViewModel snapshot() { return snapshot; }
    public static void clear() { snapshot=ViewModel.EMPTY; }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        Network.clientReceiver=m -> {
            if (m.kind().equals("snapshot")) {
                snapshot=Network.decode(m.text());
                ChecklistUi.setClientModel(snapshot);
                ChecklistUi.prewarmClient(snapshot);
            }
            else if (m.kind().equals("toast")) Minecraft.getInstance().getToasts().addToast(SystemToast.multiline(Minecraft.getInstance(),SystemToast.SystemToastId.PERIODIC_NOTIFICATION,Component.translatable("camp_checklist.toast.title"),Component.literal(m.text())));
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            while (OPEN_KEY.consumeClick()) Network.requestOpen();
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> { clear(); ChecklistUi.clearClientInstances(); });
    }
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent e) { e.register(OPEN_KEY); }
}
