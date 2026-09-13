package com.kelp.campchecklist.internal.condition;

import com.kelp.campchecklist.CampChecklist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public final class ConditionRegistration {
    public static final ConditionRegistry REGISTRY = new ConditionRegistry();
    private ConditionRegistration() {}
    public static void install(IEventBus modBus) {
        modBus.addListener(BuiltinConditions::register);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            REGISTRY.initialize(ModLoader::postEvent);
            CampChecklist.LOGGER.info("Checklist condition registration complete: types={}, frozen={}", REGISTRY.size(), REGISTRY.frozen());
        }));
    }
}
