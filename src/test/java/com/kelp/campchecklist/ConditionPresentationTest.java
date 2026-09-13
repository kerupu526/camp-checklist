package com.kelp.campchecklist;

import com.kelp.campchecklist.api.condition.ConditionDisplay;
import com.kelp.campchecklist.api.progress.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionPresentationTest {
    @Test void recursiveCopyPreservesComponentsIconsAndPreorderProjection() {
        ConditionResult leaf=new ConditionResult(ConditionStatus.SATISFIED,Optional.of(new NumericProgress(128,128)),List.of(),Optional.empty(),Optional.of(Component.literal("128 / 128")));
        ConditionResult root=new ConditionResult(ConditionStatus.UNSATISFIED,Optional.of(new NumericProgress(1,3)),List.of(
                detail("logic",Component.translatable("item.minecraft.diamond"),Items.DIAMOND,leaf),
                detail("calculation",Component.literal("Calculation Processor"),Items.REDSTONE,leaf),
                detail("engineering",Component.literal("Engineering Processor"),Items.QUARTZ,leaf)),Optional.empty());
        ViewModel.Evaluation view=ConditionPresentation.copy(root,new int[]{ConditionPresentation.MAX_DETAIL_NODES_PER_SNAPSHOT});
        assertTrue(view.details().getFirst().display().label().getContents().toString().contains("item.minecraft.diamond"));
        assertEquals(Items.DIAMOND,view.details().getFirst().display().icon().orElseThrow().getItem());
        assertEquals(List.of("minecraft:diamond","minecraft:redstone","minecraft:quartz"),ConditionPresentation.rows(view).stream().filter(d->!d.id().contains("/")).map(ViewModel.Detail::id).toList());
        assertEquals("Diamond",ConditionPresentation.rows(view).getFirst().label());
        assertEquals("128 / 128",ConditionPresentation.rows(view).getFirst().progressText());
    }
    @Test void presentationBoundsTruncateCopyWithoutChangingSemanticResult() {
        List<ConditionDetail> details=new ArrayList<>();
        for(int i=0;i<300;i++) details.add(detail("row"+i,Component.literal("row"+i),Items.STICK,ConditionResult.booleanResult(false)));
        ConditionResult root=new ConditionResult(ConditionStatus.UNSATISFIED,Optional.empty(),details,Optional.empty());
        ViewModel.Evaluation view=ConditionPresentation.copy(root,new int[]{ConditionPresentation.MAX_DETAIL_NODES_PER_GOAL});
        assertEquals(300,root.details().size());
        assertEquals(256,view.details().size());
        assertTrue(view.detailsTruncated());
    }
    @Test void progressTextIsOptionalPresentationOnlyAndDoesNotAffectTrackingIdentity() {
        ConditionResult absent=ConditionResult.booleanResult(false);
        ConditionResult present=new ConditionResult(ConditionStatus.UNSATISFIED,Optional.empty(),List.of(),Optional.empty(),Optional.of(Component.literal("custom progress")));
        assertTrue(absent.progressText().isEmpty()); assertEquals("custom progress",present.progressText().orElseThrow().getString());
        var node=new com.kelp.campchecklist.internal.condition.ConditionNode(net.minecraft.resources.ResourceLocation.parse("test:leaf"),new com.google.gson.JsonObject(),List.of());
        assertEquals(com.kelp.campchecklist.internal.condition.TrackingSignatures.signature(node),com.kelp.campchecklist.internal.condition.TrackingSignatures.signature(node));
    }
    private static ConditionDetail detail(String key,Component label,net.minecraft.world.item.Item item,ConditionResult result) {
        return new ConditionDetail(key,new ConditionDisplay(label,Optional.of(new ItemStack(item))),result);
    }
}
