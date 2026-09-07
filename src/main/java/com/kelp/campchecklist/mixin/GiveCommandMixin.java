package com.kelp.campchecklist.mixin;
import com.kelp.campchecklist.AcquisitionHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GiveCommand.class)
public class GiveCommandMixin {
    @WrapMethod(method="giveItem")
    private static int camp$excludeGive(CommandSourceStack source,ItemInput item,Collection<ServerPlayer> players,int count,Operation<Integer> original) {
        boolean previous=AcquisitionHooks.COMMAND.get(); AcquisitionHooks.COMMAND.set(true);
        try { return original.call(source,item,players,count); }
        finally { for (ServerPlayer p:players) p.inventoryMenu.broadcastChanges(); AcquisitionHooks.COMMAND.set(previous); }
    }
}
