package com.kelp.campchecklist.mixin;
import com.kelp.campchecklist.*;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryChangeTrigger.class)
public class InventoryTriggerMixin {
    @Inject(method="trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"))
    private void camp$acquire(ServerPlayer p,Inventory inventory,ItemStack stack,CallbackInfo ci) {
        if (!AcquisitionHooks.COMMAND.get() && CampChecklist.ready(p.server)) CampChecklist.runtime(p.server).acquire(p,stack);
    }
}
