package com.kelp.campchecklist;

import com.kelp.campchecklist.api.progress.ConditionStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkSnapshotCodecTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)); }
    @Test void protocolTwoRoundTripsRecursiveSnapshotWithMinecraftMetadata() {
        ViewModel.Evaluation nested=new ViewModel.Evaluation(ConditionStatus.UNAVAILABLE,Optional.of(new ViewModel.Progress(2.5,4.0)),Optional.of(Component.literal("2.5 / 4")),Optional.of(Component.translatable("camp_checklist.condition.unknown","fixture")),List.of(),false);
        ViewModel.Evaluation root=new ViewModel.Evaluation(ConditionStatus.UNSATISFIED,Optional.of(new ViewModel.Progress(1.25,3.0)),Optional.empty(),Optional.empty(),List.of(new ViewModel.EvaluationDetail("logic",new ViewModel.Display(Component.translatable("item.minecraft.diamond"),Optional.of(new ItemStack(Items.DIAMOND))),nested)),true);
        ViewModel.Goal normal=new ViewModel.Goal("test:normal","test:tab","Normal","","",0,false,false,1,3,1d/3,"count","count",false,"",List.of(),root);
        ViewModel.Goal completed=new ViewModel.Goal("test:completed","test:tab","Completed","","",1,true,false,3,3,1,"count","count",false,"",List.of(),root);
        ViewModel model=new ViewModel(List.of(new ViewModel.Tab("test:tab","Tab","","minecraft:book",0)),List.of(normal,completed),List.of());
        RegistryFriendlyByteBuf encoded=buffer(); Network.Snapshot.CODEC.encode(encoded,new Network.Snapshot(model));
        RegistryFriendlyByteBuf decoded=new RegistryFriendlyByteBuf(encoded.copy(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        ViewModel received=Network.Snapshot.CODEC.decode(decoded).model();
        assertEquals(2,received.goals().size()); assertTrue(received.goals().get(1).completed());
        ViewModel.Evaluation evaluation=received.goals().getFirst().evaluation();
        assertEquals(ConditionStatus.UNSATISFIED,evaluation.status()); assertEquals(1.25,evaluation.numeric().orElseThrow().current());
        assertTrue(evaluation.detailsTruncated()); assertTrue(evaluation.details().getFirst().display().label().getContents().toString().contains("item.minecraft.diamond"));
        assertEquals(Items.DIAMOND,evaluation.details().getFirst().display().icon().orElseThrow().getItem());
        assertEquals(ConditionStatus.UNAVAILABLE,evaluation.details().getFirst().evaluation().status());
        assertEquals("2.5 / 4",evaluation.details().getFirst().evaluation().progressText().orElseThrow().getString());
    }
    @Test void malformedCountsAndExcessDepthAreRejectedWithoutAllocation() {
        RegistryFriendlyByteBuf count=buffer(); count.writeVarInt(257);
        assertThrows(IllegalArgumentException.class,()->Network.Snapshot.CODEC.decode(new RegistryFriendlyByteBuf(count.copy(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))));
        assertFalse(Network.isProtocolCompatible("1")); assertTrue(Network.isProtocolCompatible(Network.PROTOCOL_VERSION));
        assertEquals("CAMP Checklist protocol mismatch: local=2, remote=1",Network.protocolMismatchMessage("1"));
    }
    @Test void recursivePayloadBeyondDepthBoundIsRejected() {
        ViewModel.Evaluation leaf=new ViewModel.Evaluation(ConditionStatus.UNSATISFIED,Optional.empty(),Optional.empty(),Optional.empty(),List.of(),false);
        for(int depth=0;depth<=ConditionPresentation.MAX_DETAIL_DEPTH;depth++) leaf=new ViewModel.Evaluation(ConditionStatus.UNSATISFIED,Optional.empty(),Optional.empty(),Optional.empty(),List.of(new ViewModel.EvaluationDetail("d"+depth,new ViewModel.Display(Component.literal("d"+depth),Optional.empty()),leaf)),false);
        ViewModel model=new ViewModel(List.of(),List.of(new ViewModel.Goal("test:deep","test:tab","Deep","","",0,false,false,0,1,0,"count","count",false,"",List.of(),leaf)),List.of());
        RegistryFriendlyByteBuf encoded=buffer(); Network.Snapshot.CODEC.encode(encoded,new Network.Snapshot(model));
        assertThrows(IllegalArgumentException.class,()->Network.Snapshot.CODEC.decode(new RegistryFriendlyByteBuf(encoded.copy(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))));
    }
}
