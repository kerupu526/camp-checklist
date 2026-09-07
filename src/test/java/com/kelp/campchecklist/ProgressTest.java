package com.kelp.campchecklist;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressTest {
    private Definitions.Goal goal(String extra) {
        return Definitions.goal(ResourceLocation.parse("test:goal"),JsonParser.parseString("{\"tab\":\"test:tab\",\"type\":\"craft_count\",\"item\":\"minecraft:rail\","+extra+"}").getAsJsonObject());
    }
    @Test void presentationAndTargetDoNotChangeCounterIdentity() {
        assertEquals(goal("\"target\":128").signature(),goal("\"target\":256,\"title\":\"New name\",\"display_unit\":\"count\"").signature());
    }
    @Test void targetChangeKeepsCounterAndNewItemStartsAtZero() {
        ProgressStore store=new ProgressStore(); var e=store.entry("test:goal");
        e.counter(goal("\"target\":128").signature()).current=73;
        assertEquals(73,e.counter(goal("\"target\":256").signature()).current);
        var changed=Definitions.goal(ResourceLocation.parse("test:goal"),JsonParser.parseString("{\"tab\":\"test:tab\",\"type\":\"craft_count\",\"item\":\"minecraft:stick\"}").getAsJsonObject());
        assertEquals(0,e.counter(changed.signature()).current);
    }
    @Test void internalUnitChangeStartsCounterAtZero() {
        ProgressStore store=new ProgressStore(); var e=store.entry("test:goal");
        e.counter(goal("\"target\":128").signature()).current=73;
        assertEquals(0,e.counter(goal("\"target\":128,\"unit\":\"blocks\",\"display_unit\":\"blocks\"").signature()).current);
    }
    @Test void saveRoundTripPreservesOrphansCompletionToastAndOpaqueState() {
        ProgressStore store=new ProgressStore(); var e=store.entry("removed:goal"); e.completed=true; e.toastShown=true;
        e.activeSignature="old signature";
        var c=e.counter("old signature"); c.current=73; c.version=2; c.state.putString("integration:player","alice");
        var restored=ProgressStore.load(store.save()).entry("removed:goal");
        assertTrue(restored.completed); assertTrue(restored.toastShown);
        assertEquals("old signature",restored.activeSignature);
        assertEquals(73,restored.counter("old signature").current);
        assertEquals(2,restored.counter("old signature").version);
        assertEquals("alice",restored.counter("old signature").state.getString("integration:player"));
    }
    @Test void manualUncheckDoesNotEraseToastRecord() {
        ProgressStore store=new ProgressStore(); var e=store.entry("manual:goal"); e.completed=true; e.toastShown=true; e.completed=false;
        var loaded=ProgressStore.load(store.save()).entry("manual:goal"); assertFalse(loaded.completed); assertTrue(loaded.toastShown);
    }
    @Test void invalidCounterTargetsRejected() {
        assertThrows(IllegalArgumentException.class,() -> goal("\"target\":0"));
        assertThrows(IllegalArgumentException.class,() -> goal("\"target\":-1"));
        assertThrows(IllegalArgumentException.class,() -> goal("\"target\":1e999"));
    }
    @Test void countAndDistanceDisplayUnitsCannotBeMixed() {
        assertThrows(IllegalArgumentException.class, () -> goal("\"unit\":\"count\",\"display_unit\":\"kilometers\""));
        assertThrows(IllegalArgumentException.class, () -> goal("\"unit\":\"blocks\",\"display_unit\":\"count\""));
    }
    @Test void missingOrAmbiguousMatchersRejected() {
        assertThrows(IllegalArgumentException.class,() -> goal("\"tag\":\"c:rails\""));
    }
    @Test void customParameterOrderDoesNotInvalidateCounter() {
        assertEquals(goal("\"parameters\":{\"a\":1,\"b\":2}").signature(),goal("\"parameters\":{\"b\":2,\"a\":1}").signature());
        assertNotEquals(goal("\"parameters\":{\"a\":1}").signature(),goal("\"parameters\":{\"a\":2}").signature());
    }
    @Test void pneumaticArmorScoreCountsArmorAndTierRequirements() {
        assertEquals(0, PneumaticCraftIntegration.score(java.util.List.of(false, false, false, false), java.util.List.of(0, 0, 0), java.util.List.of(1, 1, 1)));
        assertEquals(4, PneumaticCraftIntegration.score(java.util.List.of(true, true, true, true), java.util.List.of(0, 0, 0), java.util.List.of(1, 1, 1)));
        assertEquals(5, PneumaticCraftIntegration.score(java.util.List.of(true, true, true, true), java.util.List.of(1, 0, 0), java.util.List.of(1, 1, 1)));
        assertEquals(7, PneumaticCraftIntegration.score(java.util.List.of(true, true, true, true), java.util.List.of(1, 1, 1), java.util.List.of(1, 1, 1)));
        assertEquals(4, PneumaticCraftIntegration.score(java.util.List.of(true, true, true, true), java.util.List.of(0, 1, 0), java.util.List.of(1, 2, 1)));
        assertEquals(1, PneumaticCraftIntegration.score(java.util.List.of(false, false, false, false), java.util.List.of(1), java.util.List.of(1)));
    }
    @Test void pneumaticArmorUsesHighestSinglePlayerScoreWithoutSumming() {
        assertEquals(4, PneumaticCraftIntegration.maxSinglePlayerScore(java.util.List.of(4, 3)));
        assertEquals(5, PneumaticCraftIntegration.maxSinglePlayerScore(java.util.List.of(4, 5)));
        assertEquals(0, PneumaticCraftIntegration.maxSinglePlayerScore(java.util.List.of()));
    }
    @Test void completedGoalViewKeepsTargetAsVisibleCurrent() {
        assertEquals(7, ChecklistRuntime.viewCurrent(7, true, 0));
        assertEquals(4, ChecklistRuntime.viewCurrent(7, false, 4));
    }
    @Test void unavailableGoalsExcludedFromCompletionAndUnitsConvert() {
        var g=new ViewModel.Goal("a","tab","title","","",0,true,false,8400,16000,1,"blocks","kilometers",false,"");
        var unavailable=new ViewModel.Goal("b","tab","title","","",0,false,false,0,1,0,"count","count",true,"missing");
        assertEquals(8.4,g.displayCurrent()); assertEquals(16,g.displayTarget());
        assertEquals(1,new ViewModel(java.util.List.of(),java.util.List.of(g,unavailable),java.util.List.of()).completionFraction());
    }
    @Test void createTrainDistanceUsesHighestPlayerState() {
        var state=new net.minecraft.nbt.CompoundTag();
        var players=new net.minecraft.nbt.CompoundTag();
        var first=new net.minecraft.nbt.CompoundTag(); first.putDouble("distance",12000);
        var second=new net.minecraft.nbt.CompoundTag(); second.putDouble("distance",15000);
        players.put("first",first); players.put("second",second); state.put("players",players);
        assertEquals(15000,CreateIntegration.maxPlayerDistance(state));
    }
    @Test void acquireItemSetRecordsDistinctItemsAndIsSticky() {
        if (CheckerRegistry.get("camp_checklist:acquire_item_set") == null) CoreCheckers.register();
        var goal = Definitions.goal(ResourceLocation.parse("test:set"), JsonParser.parseString("""
                {"tab":"test:tab","type":"custom","checker":"camp_checklist:acquire_item_set","target":2,
                 "parameters":{"items":["minecraft:stick","minecraft:stone"]}}
                """).getAsJsonObject());
        var checker = CheckerRegistry.get("camp_checklist:acquire_item_set");
        assertNotNull(checker);
        var state = new net.minecraft.nbt.CompoundTag();
        checker.onAcquire(goal, null, null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), state, 0);
        assertEquals(1, state.getList("acquired_items", net.minecraft.nbt.Tag.TAG_STRING).size());
        var first = checker.evaluate(goal, null, state.copy(), 0);
        assertEquals(1, first.current());
        checker.onAcquire(goal, null, null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), state, first.version());
        assertEquals(1, checker.evaluate(goal, null, state.copy(), first.version()).current());
        checker.onAcquire(goal, null, null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE), state, first.version());
        assertTrue(checker.evaluate(goal, null, state.copy(), first.version()).completed());
    }
    @Test void acquireItemSetSignatureChangesWhenRequiredItemsChange() {
        var first = Definitions.goal(ResourceLocation.parse("test:set"), JsonParser.parseString("""
                {"tab":"test:tab","type":"custom","checker":"camp_checklist:acquire_item_set","target":1,
                 "parameters":{"items":["minecraft:stick"]}}
                """).getAsJsonObject());
        var second = Definitions.goal(ResourceLocation.parse("test:set"), JsonParser.parseString("""
                {"tab":"test:tab","type":"custom","checker":"camp_checklist:acquire_item_set","target":1,
                 "parameters":{"items":["minecraft:stone"]}}
                """).getAsJsonObject());
        assertNotEquals(first.signature(), second.signature());
    }
}
