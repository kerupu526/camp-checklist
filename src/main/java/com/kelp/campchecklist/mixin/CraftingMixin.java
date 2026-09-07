package com.kelp.campchecklist.mixin;
import com.kelp.campchecklist.*;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures removeCount before NeoForge's crafting event; stack count can differ on shift-craft. */
@Mixin(ResultSlot.class)
public class CraftingMixin {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;
    @Inject(method="checkTakeAchievements",at=@At("HEAD"))
    private void camp$craft(ItemStack stack,CallbackInfo ci) {
        if (removeCount>0 && player instanceof ServerPlayer p && CampChecklist.ready(p.server)) CampChecklist.runtime(p.server).craft(p,stack.copyWithCount(removeCount));
    }
}
