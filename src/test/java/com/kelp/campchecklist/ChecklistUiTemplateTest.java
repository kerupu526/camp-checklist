package com.kelp.campchecklist;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChecklistUiTemplateTest {
    private static final Path TEMPLATE = Path.of(
            "../..", "src/main/resources/assets/camp_checklist/resources/ui/checklist_ui.ui.nbt");

    @Test
    void copiedAuthoredContentKeepsAnIndependentScrollerAndCardSubtree() throws Exception {
        var template = loadTemplate();
        var root = new UIElement();
        template.initUI(root);

        UIElement authored = root.selectId("create-content").findFirst().orElseThrow();
        UIElement firstCopy = authored.copy();
        UIElement secondCopy = authored.copy();

        ScrollerView authoredScroller = authored.selfAndAllChildren()
                .filter(ScrollerView.class::isInstance).map(ScrollerView.class::cast).findFirst().orElseThrow();
        ScrollerView firstScroller = firstCopy.selfAndAllChildren()
                .filter(ScrollerView.class::isInstance).map(ScrollerView.class::cast).findFirst().orElseThrow();
        ScrollerView secondScroller = secondCopy.selfAndAllChildren()
                .filter(ScrollerView.class::isInstance).map(ScrollerView.class::cast).findFirst().orElseThrow();

        assertNotSame(authoredScroller, firstScroller);
        assertNotSame(firstScroller, secondScroller);
        assertEquals(authoredScroller.viewContainer.getChildren().size(), firstScroller.viewContainer.getChildren().size());
        assertEquals(authoredScroller.viewContainer.getChildren().size(), secondScroller.viewContainer.getChildren().size());

        Label firstTitle = firstCopy.selectId("goal-container").findFirst().orElseThrow()
                .selfAndAllChildren().filter(Label.class::isInstance).map(Label.class::cast).findFirst().orElseThrow();
        Label secondTitle = secondCopy.selectId("goal-container").findFirst().orElseThrow()
                .selfAndAllChildren().filter(Label.class::isInstance).map(Label.class::cast).findFirst().orElseThrow();
        firstTitle.setText(Component.literal("first"));
        secondTitle.setText(Component.literal("second"));

        assertEquals("first", firstTitle.getText().getString());
        assertEquals("second", secondTitle.getText().getString());
    }

    @Test
    void authoredTemplateNormalizesFourTabsAndBindsFifteenGoalsWithoutCrossTabCopies() throws Exception {
        ViewModel model = modelWithFifteenGoals();
        var fixture = ChecklistUi.renderTemplateForTest(loadTemplate(), model, true);
        var ui = fixture.ui();
        var root = ui.getRootElement();
        var tabView = root.selectId("tab-view", TabView.class).findFirst().orElseThrow();
        var audit = TabViewTopology.inspect(tabView);

        assertTrue(audit.isConsistent());
        assertEquals(4, audit.headers().size());
        assertEquals(4, audit.contents().size());
        assertEquals(4, audit.registered().size());
        assertEquals("camp_checklist:create", tabView.getSelectedTab().getId());

        Map<String, List<ViewModel.Goal>> expected = new LinkedHashMap<>();
        for (ViewModel.Goal goal : model.goals()) {
            expected.computeIfAbsent(goal.tab(), ignored -> new ArrayList<>()).add(goal);
        }
        for (Map.Entry<String, List<ViewModel.Goal>> entry : expected.entrySet()) {
            UIElement content = tabView.getTabContents().entrySet().stream()
                    .filter(pair -> pair.getKey().getId().equals(entry.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            ScrollerView scroller = content.selfAndAllChildren()
                    .filter(ScrollerView.class::isInstance).map(ScrollerView.class::cast).findFirst().orElseThrow();
            List<UIElement> cards = scroller.viewContainer.getChildren();

            assertEquals(entry.getValue().size(), cards.size(), entry.getKey());
            assertTrue(scroller.verticalScroller.scrollBar.isActive(), entry.getKey());
            assertTrue(scroller.verticalScroller.scrollBar.isAllowHitTest(), entry.getKey());
            assertIterableEquals(entry.getValue().stream().map(ViewModel.Goal::title).toList(),
                    cards.stream().map(ChecklistUiTemplateTest::cardTitle).toList(), entry.getKey());
            assertIterableEquals(entry.getValue().stream().map(ViewModel.Goal::description).toList(),
                    cards.stream().map(ChecklistUiTemplateTest::cardDescription).toList(), entry.getKey());
        }
        assertEquals(4, fixture.dragBoundScrollerCount());

        UIElement createFirst = firstCard(tabView, "camp_checklist:create");
        UIElement mekanismFirst = firstCard(tabView, "camp_checklist:mekanism");
        assertEquals("16 / 16 km", progressText(createFirst));
        assertEquals("0 / 1", progressText(mekanismFirst));
        assertNotSame(scrollerFor(tabView, "camp_checklist:create"), scrollerFor(tabView, "camp_checklist:mekanism"));

        List<UIElement> originalHeaders = List.copyOf(audit.headers());
        List<UIElement> originalContents = List.copyOf(audit.contents());
        Map<String, List<UIElement>> originalCards = new LinkedHashMap<>();
        for (String tabId : expected.keySet()) {
            originalCards.put(tabId, List.copyOf(scrollerFor(tabView, tabId).viewContainer.getChildren()));
        }
        tabView.selectTab(audit.headers().get(1));

        ViewModel updated = withUpdatedFirstGoal(model);
        fixture.refresh(updated);
        var refreshedAudit = TabViewTopology.inspect(tabView);
        assertIterableEquals(originalHeaders, refreshedAudit.headers());
        assertIterableEquals(originalContents, refreshedAudit.contents());
        for (String tabId : expected.keySet()) {
            assertIterableEquals(originalCards.get(tabId), scrollerFor(tabView, tabId).viewContainer.getChildren());
        }
        assertEquals("camp_checklist:mekanism", tabView.getSelectedTab().getId());
        assertEquals("기차로 8km 이동", cardTitle(firstCard(tabView, "camp_checklist:create")));
        assertEquals("8 / 16 km", progressText(firstCard(tabView, "camp_checklist:create")));
    }

    @Test
    void authoredTemplateProvidesStableCardLeafIdsAndOreCheckmark() throws Exception {
        var root = loadTemplate().createUI().getRootElement();
        List<UIElement> prototypes = root.selectId("first-card").toList();
        assertFalse(prototypes.isEmpty());
        for (UIElement card : prototypes) {
            assertEquals(1, card.selectId("goal-title", Label.class).count());
            assertEquals(1, card.selectId("goal-description", Label.class).count());
            assertEquals(1, card.selectId("goal-progress-bar", com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar.class).count());
            assertEquals(1, card.selectId("goal-progress-text", Label.class).count());
            assertEquals(1, card.selectId("goal-toggle", Toggle.class).count());

            Toggle toggle = card.selectId("goal-toggle", Toggle.class).findFirst().orElseThrow();
            toggle.setOn(true, false);
            assertTrue(toggle.isOn());
            assertTrue(toggle.markIcon.isDisplayed(), "Ore checkmark must be visible when checked");
        }

        CompoundTag rootInline = NbtIo.read(TEMPLATE).getCompound("data").getCompound("template").getCompound("inline");
        assertEquals(500, rootInline.getCompound("width").getInt("value"));
        assertEquals(300, rootInline.getCompound("height").getInt("value"));
        assertDimension(rootInline, "min-height", "LENGTH", 0);
        assertDimension(rootInline, "max-height", "LENGTH", 300);
        assertTrue(root.selectId("goal-details").count() >= 1);

        CompoundTag data = NbtIo.read(TEMPLATE).getCompound("data").getCompound("template");
        List<CompoundTag> authoredToggles = findAllElementData(data, "goal-toggle");
        assertEquals(2, authoredToggles.size());
        for (CompoundTag toggle : authoredToggles) {
            CompoundTag mark = toggle.getCompound("inline").getCompound("mark-background");
            assertEquals("sprite_texture", mark.getString("type"));
            CompoundTag texture = mark.getCompound("data");
            assertEquals("ldlib2:textures/gui/ore_styles.png", texture.getString("imageLocation"));
            assertIterableEquals(List.of(50, 35), asList(texture.getIntArray("spritePosition")));
            assertIterableEquals(List.of(10, 10), asList(texture.getIntArray("spriteSize")));
        }
    }

    @Test
    void authoredViewportFillsRemainingHeightAndDescriptionsWrap() throws Exception {
        CompoundTag resource = NbtIo.read(TEMPLATE);
        CompoundTag data = resource.getCompound("data").getCompound("template");
        CompoundTag tabViewInline = findElementData(data, "tab-view").getCompound("inline");
        assertDimension(tabViewInline, "min-height", "LENGTH", 0);
        assertDimension(tabViewInline, "flex-basis", "LENGTH", 0);

        ListTag stylesheets = resource.getCompound("data").getList("stylesheets", Tag.TAG_STRING);
        assertTrue(stylesheets.stream().anyMatch(tag -> "camp_checklist:lss/checklist.lss".equals(tag.getAsString())));
        String stylesheet = Files.readString(Path.of("../..", "src/main/resources/assets/camp_checklist/lss/checklist.lss"));
        assertTrue(stylesheet.contains(".__tab-view_tab_content_container__"));
        assertTrue(stylesheet.contains("min-height: 0"));
        assertTrue(stylesheet.contains("flex-basis: 0"));

        for (String id : List.of("create-content", "mekanism-content", "ae2-content", "pnc-content")) {
            CompoundTag content = findElementData(data, id);
            CompoundTag inline = content.getCompound("inline");
            assertDimension(inline, "width", "PERCENT", 1);
            assertFalse(inline.contains("height"), id + " must size from the remaining flex height");
            assertDimension(inline, "min-height", "LENGTH", 0);
            assertEquals(1, inline.getFloat("flex-grow"), id);
            assertDimension(inline, "flex-basis", "LENGTH", 0);
        }

        CompoundTag cardContainer = findElementData(data, "card-container").getCompound("inline");
        assertDimension(cardContainer, "width", "PERCENT", 1);
        assertFalse(cardContainer.contains("height"), "card-container must not add 100% height below its header");
        assertDimension(cardContainer, "min-height", "LENGTH", 0);
        assertEquals("COLUMN", cardContainer.getString("flex-direction"));
        assertDimension(cardContainer, "flex-basis", "LENGTH", 0);

        for (String id : List.of("first-card", "title-container", "progress-container", "goal-details", "goal-detail-prototype")) {
            for (CompoundTag element : findAllElementData(data, id)) {
                assertFalse(element.getCompound("inline").contains("width"),
                        id + " must stretch inside the viewport without adding padding to 100% width");
            }
        }
        for (CompoundTag progressContainer : findAllElementData(data, "progress-container")) {
            assertDimension(progressContainer.getCompound("inline"), "padding-left", "LENGTH", 6);
            assertDimension(progressContainer.getCompound("inline"), "padding-right", "LENGTH", 6);
        }

        CompoundTag cardList = findElementData(data, "card-list");
        CompoundTag cardListInline = cardList.getCompound("inline");
        assertFalse(cardListInline.contains("height"), "card-list must not retain a fixed pixel height");
        assertDimension(cardListInline, "min-height", "LENGTH", 0);
        assertEquals(1, cardListInline.getFloat("flex-grow"));
        assertDimension(cardListInline, "flex-basis", "LENGTH", 0);

        CompoundTag scrollerData = findTypedWrapper(cardList, "scroller-view").getCompound("data");
        CompoundTag scrollerInline = scrollerData.getCompound("inline");
        assertFalse(scrollerInline.contains("height"), "ScrollerView must not retain a fixed pixel height");
        assertDimension(scrollerInline, "min-height", "LENGTH", 0);
        assertEquals(1, scrollerInline.getFloat("flex-grow"));
        assertDimension(scrollerInline, "flex-basis", "LENGTH", 0);
        assertEquals("VERTICAL", scrollerInline.getString("scroller-view-mode"));
        assertEquals("ALWAYS", scrollerInline.getString("scroller-vertical-display"));
        assertEquals("NEVER", scrollerInline.getString("scroller-horizontal-display"));
        assertFalse(scrollerInline.getBoolean("adaptive-width"));
        assertFalse(scrollerInline.getBoolean("adaptive-height"));

        // LDLib2 deliberately skips inline-style deserialization on the server-side JUnit runtime,
        // so verify the authored NBT contract directly instead of asserting client-computed styles.
        for (CompoundTag descriptionData : findAllElementData(data, "goal-description")) {
            CompoundTag descriptionInline = descriptionData.getCompound("inline");
            assertDimension(descriptionInline, "width", "PERCENT", 1);
            assertDimension(descriptionInline, "min-width", "LENGTH", 0);
            assertFalse(descriptionInline.contains("height"), "Description must grow with wrapped text");
            assertEquals("WRAP", descriptionInline.getString("text-wrap"));
            assertFalse(descriptionInline.getBoolean("adaptive-width"));
            assertTrue(descriptionInline.getBoolean("adaptive-height"));
        }
    }

    @Test
    void compositeDetailsUseItemComponentsSingleProgressLabelAndStableRows() throws Exception {
        ViewModel model = modelWithFifteenGoals();
        var fixture = ChecklistUi.renderTemplateForTest(loadTemplate(), model, true);
        UIElement card = fixture.ui().getRootElement().selectId("goal-camp_checklist_ae2-0")
                .findFirst().orElseThrow();
        UIElement details = card.selectId("goal-details").findFirst().orElseThrow();
        assertFalse(fixture.isDetailsExpanded("camp_checklist:ae2-0"));
        assertEquals("NONE", findElementData(NbtIo.read(TEMPLATE).getCompound("data"), "goal-details")
                .getCompound("inline").getString("display"));
        assertEquals(1, details.selectId("detail-divider").count());
        assertEquals(3, details.selectId("detail-icon", ItemSlot.class).count());
        assertEquals(3, details.selectId("detail-name", Label.class).count());
        assertEquals(3, details.selectId("detail-progress", Label.class).count());
        assertEquals(0, details.selectId("detail-current").count());
        assertEquals(0, details.selectId("detail-separator").count());
        assertEquals(0, details.selectId("detail-target").count());

        List<UIElement> rows = details.getChildren().stream()
                .filter(child -> child.getId().equals("goal-detail-prototype")).toList();
        assertEquals(3, rows.size());
        assertEquals(Items.DIAMOND, rows.getFirst().selectId("detail-icon", ItemSlot.class)
                .findFirst().orElseThrow().getValue().getItem());
        assertEquals(new ItemStack(Items.DIAMOND).getHoverName(),
                rows.getFirst().selectId("detail-name", Label.class).findFirst().orElseThrow().getText());
        assertEquals("0 / 128",
                rows.getFirst().selectId("detail-progress", Label.class).findFirst().orElseThrow().getText().getString());

        fixture.setDetailsExpanded("camp_checklist:ae2-0", true);
        assertTrue(fixture.isDetailsExpanded("camp_checklist:ae2-0"));
        fixture.refresh(withUpdatedFirstGoal(model));
        assertSame(details, card.selectId("goal-details").findFirst().orElseThrow());
        assertIterableEquals(rows, details.getChildren().stream()
                .filter(child -> child.getId().equals("goal-detail-prototype")).toList());
        assertTrue(fixture.isDetailsExpanded("camp_checklist:ae2-0"));

        ViewModel oneDetail = withAe2Details(model, List.of(
                new ViewModel.Detail("minecraft:diamond", "item.minecraft.diamond", 32, 128, "count", "count")));
        fixture.refresh(oneDetail);
        assertSame(details, card.selectId("goal-details").findFirst().orElseThrow());
        assertEquals(1, details.selectId("detail-progress", Label.class).count());
        assertEquals("32 / 128", details.selectId("detail-progress", Label.class)
                .findFirst().orElseThrow().getText().getString());

        fixture.refresh(withAe2Details(model, List.of()));
        assertEquals(0, details.selectId("goal-detail-prototype").count());
        assertFalse(fixture.isDetailsExpanded("camp_checklist:ae2-0"));

        fixture.refresh(withAe2Details(model, List.of(
                new ViewModel.Detail("minecraft:redstone", "item.minecraft.redstone", 1, 2, "count", "count"),
                new ViewModel.Detail("minecraft:quartz", "item.minecraft.quartz", 2, 2, "count", "count"))));
        assertEquals(2, details.selectId("goal-detail-prototype").count());
        fixture.setDetailsExpanded("camp_checklist:ae2-0", true);
        assertTrue(fixture.isDetailsExpanded("camp_checklist:ae2-0"));

        CompoundTag data = NbtIo.read(TEMPLATE).getCompound("data").getCompound("template");
        CompoundTag iconInline = findElementData(data, "detail-icon").getCompound("inline");
        assertDimension(iconInline, "width", "LENGTH", 16);
        assertDimension(iconInline, "height", "LENGTH", 16);
        CompoundTag nameInline = findElementData(data, "detail-name").getCompound("inline");
        assertDimension(nameInline, "min-width", "LENGTH", 0);
        assertEquals(1, nameInline.getFloat("flex-grow"));
        CompoundTag progressInline = findElementData(data, "detail-progress").getCompound("inline");
        assertDimension(progressInline, "width", "LENGTH", 64);
        assertEquals("RIGHT", progressInline.getString("horizontal-align"));
        for (CompoundTag detailsData : findAllElementData(data, "goal-details")) {
            CompoundTag detailsInline = detailsData.getCompound("inline");
            assertDimension(detailsInline, "padding-top", "LENGTH", 4);
            assertDimension(detailsInline, "padding-bottom", "LENGTH", 4);
        }
        for (CompoundTag row : findAllElementData(data, "goal-detail-prototype")) {
            CompoundTag rowInline = row.getCompound("inline");
            assertDimension(rowInline, "padding-horizontal", "LENGTH", 2);
            assertDimension(rowInline, "padding-vertical", "LENGTH", 2);
        }
    }

    @Test
    void refreshDoesNotAccumulateManualToggleCallbacks() throws Exception {
        ViewModel model = withManualGoal(modelWithFifteenGoals(), "camp_checklist:create-rails", false);
        List<String> requests = new ArrayList<>();
        var fixture = ChecklistUi.renderTemplateForTest(loadTemplate(), model, true,
                (id, value) -> requests.add(id + "=" + value));

        fixture.refresh(model);
        fixture.refresh(model);
        Toggle toggle = fixture.ui().getRootElement().selectId("goal-camp_checklist_create-rails")
                .findFirst().orElseThrow().selectId("goal-toggle", Toggle.class).findFirst().orElseThrow();
        toggle.setOn(true, true);
        assertIterableEquals(List.of("camp_checklist:create-rails=true"), requests);

        fixture.refresh(withManualGoal(model, "camp_checklist:create-rails", true));
        toggle.setOn(false, true);
        assertEquals(1, requests.size(), "Unavailable manual goals must not retain an active request callback");
    }

    private static UITemplate loadTemplate() throws Exception {
        var resource = NbtIo.read(TEMPLATE);
        return UITemplate.CODEC.parse(NbtOps.INSTANCE, resource.getCompound("data")).getOrThrow();
    }

    private static UIElement firstCard(TabView tabView, String tabId) {
        return scrollerFor(tabView, tabId).viewContainer.getChildren().getFirst();
    }

    private static ScrollerView scrollerFor(TabView tabView, String tabId) {
        UIElement content = tabView.getTabContents().entrySet().stream()
                .filter(pair -> pair.getKey().getId().equals(tabId))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        return content.selfAndAllChildren().filter(ScrollerView.class::isInstance)
                .map(ScrollerView.class::cast).findFirst().orElseThrow();
    }

    private static String cardTitle(UIElement card) {
        return card.selectId("goal-title", Label.class).findFirst().orElseThrow().getText().getString();
    }

    private static String cardDescription(UIElement card) {
        return card.selectId("goal-description", Label.class).findFirst().orElseThrow().getText().getString();
    }

    private static String progressText(UIElement card) {
        return card.selectId("goal-progress-text", Label.class).findFirst().orElseThrow().getText().getString();
    }

    private static CompoundTag findElementData(Tag tag, String id) {
        if (tag instanceof CompoundTag compound) {
            if (id.equals(compound.getString("id"))) return compound;
            for (String key : compound.getAllKeys()) {
                CompoundTag found = findElementDataOrNull(compound.get(key), id);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                CompoundTag found = findElementDataOrNull(child, id);
                if (found != null) return found;
            }
        }
        throw new IllegalStateException("Missing authored element " + id);
    }

    private static CompoundTag findElementDataOrNull(Tag tag, String id) {
        if (tag == null) return null;
        if (tag instanceof CompoundTag compound) {
            if (id.equals(compound.getString("id"))) return compound;
            for (String key : compound.getAllKeys()) {
                CompoundTag found = findElementDataOrNull(compound.get(key), id);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                CompoundTag found = findElementDataOrNull(child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<CompoundTag> findAllElementData(Tag tag, String id) {
        List<CompoundTag> matches = new ArrayList<>();
        collectElementData(tag, id, matches);
        return matches;
    }

    private static void collectElementData(Tag tag, String id, List<CompoundTag> matches) {
        if (tag instanceof CompoundTag compound) {
            if (id.equals(compound.getString("id"))) matches.add(compound);
            for (String key : compound.getAllKeys()) collectElementData(compound.get(key), id, matches);
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) collectElementData(child, id, matches);
        }
    }

    private static List<Integer> asList(int[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    private static CompoundTag findTypedWrapper(Tag tag, String type) {
        if (tag instanceof CompoundTag compound) {
            if (type.equals(compound.getString("type"))) return compound;
            for (String key : compound.getAllKeys()) {
                CompoundTag found = findTypedWrapperOrNull(compound.get(key), type);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                CompoundTag found = findTypedWrapperOrNull(child, type);
                if (found != null) return found;
            }
        }
        throw new IllegalStateException("Missing typed wrapper " + type);
    }

    private static CompoundTag findTypedWrapperOrNull(Tag tag, String type) {
        if (tag == null) return null;
        if (tag instanceof CompoundTag compound) {
            if (type.equals(compound.getString("type"))) return compound;
            for (String key : compound.getAllKeys()) {
                CompoundTag found = findTypedWrapperOrNull(compound.get(key), type);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                CompoundTag found = findTypedWrapperOrNull(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertDimension(CompoundTag inline, String key, String type, float value) {
        assertTrue(inline.contains(key), "Missing " + key);
        assertEquals(type, inline.getCompound(key).getString("type"), key);
        assertEquals(value, inline.getCompound(key).getFloat("value"), key);
    }

    private static ViewModel modelWithFifteenGoals() {
        List<ViewModel.Tab> tabs = List.of(
                new ViewModel.Tab("camp_checklist:create", "Create", "Create goals", "", 10),
                new ViewModel.Tab("camp_checklist:mekanism", "Mekanism", "Mekanism goals", "", 20),
                new ViewModel.Tab("camp_checklist:ae2", "AE2", "AE2 goals", "", 30),
                new ViewModel.Tab("camp_checklist:pneumaticcraft", "PneumaticCraft", "PNC goals", "", 40));
        List<ViewModel.Goal> goals = new ArrayList<>();
        goals.add(goal("create-train", tabs.get(0).id(), "기차로 16km 이동", "Create train description", 10,
                true, 16_000, 16_000, "blocks", "kilometers"));
        goals.add(goal("create-rails", tabs.get(0).id(), "철도 건설 준비", "Create rails description", 20,
                false, 64, 128, "count", "count"));
        goals.add(goal("create-review", tabs.get(0).id(), "케이크 공장 자동화 검토", "Create review description", 30,
                false, 0, 1, "count", "count"));
        goals.add(goal("mek-antimatter", tabs.get(1).id(), "반물질 펠릿 획득", "Mekanism antimatter description", 10,
                false, 0, 1, "count", "count"));
        addGenericGoals(goals, tabs.get(1).id(), "Mekanism", 3);
        addGenericGoals(goals, tabs.get(2).id(), "AE2", 4);
        addGenericGoals(goals, tabs.get(3).id(), "PneumaticCraft", 4);
        return new ViewModel(tabs, goals, List.of());
    }

    private static ViewModel withUpdatedFirstGoal(ViewModel original) {
        List<ViewModel.Goal> goals = original.goals().stream().map(goal -> {
            if (!goal.id().equals("camp_checklist:create-train")) return goal;
            return new ViewModel.Goal(goal.id(), goal.tab(), "기차로 8km 이동", goal.description(), goal.icon(), goal.order(),
                    false, goal.manual(), 8_000, goal.target(), 0.5,
                    goal.unit(), goal.displayUnit(), goal.unavailable(), goal.reason());
        }).toList();
        return new ViewModel(original.tabs(), goals, original.diagnostics());
    }

    private static ViewModel withAe2Details(ViewModel original, List<ViewModel.Detail> details) {
        List<ViewModel.Goal> goals = original.goals().stream().map(goal -> {
            if (!goal.id().equals("camp_checklist:ae2-0")) return goal;
            return new ViewModel.Goal(goal.id(), goal.tab(), goal.title(), goal.description(), goal.icon(), goal.order(),
                    goal.completed(), goal.manual(), goal.current(), goal.target(), goal.progress(),
                    goal.unit(), goal.displayUnit(), goal.unavailable(), goal.reason(), details);
        }).toList();
        return new ViewModel(original.tabs(), goals, original.diagnostics());
    }

    private static ViewModel withManualGoal(ViewModel original, String goalId, boolean unavailable) {
        List<ViewModel.Goal> goals = original.goals().stream().map(goal -> {
            if (!goal.id().equals(goalId)) return goal;
            return new ViewModel.Goal(goal.id(), goal.tab(), goal.title(), goal.description(), goal.icon(), goal.order(),
                    goal.completed(), true, goal.current(), goal.target(), goal.progress(),
                    goal.unit(), goal.displayUnit(), unavailable, unavailable ? "Unavailable for test" : goal.reason(),
                    goal.details());
        }).toList();
        return new ViewModel(original.tabs(), goals, original.diagnostics());
    }

    private static void addGenericGoals(List<ViewModel.Goal> goals, String tabId, String prefix, int count) {
        for (int index = 0; index < count; index++) {
            ViewModel.Goal goal = goal(prefix.toLowerCase() + '-' + index, tabId, prefix + " goal " + index,
                    prefix + " description " + index, 20 + index * 10,
                    index % 2 == 0, index, count, "count", "count");
            if (prefix.equals("AE2") && index == 0) {
                goal = new ViewModel.Goal(goal.id(), goal.tab(), goal.title(), goal.description(), goal.icon(), goal.order(),
                        goal.completed(), goal.manual(), goal.current(), goal.target(), goal.progress(),
                        goal.unit(), goal.displayUnit(), goal.unavailable(), goal.reason(), List.of(
                        new ViewModel.Detail("minecraft:diamond", "item.minecraft.diamond", 0, 128, "count", "count"),
                        new ViewModel.Detail("minecraft:redstone", "item.minecraft.redstone", 64, 128, "count", "count"),
                        new ViewModel.Detail("minecraft:quartz", "item.minecraft.quartz", 128, 128, "count", "count")));
            }
            goals.add(goal);
        }
    }

    private static ViewModel.Goal goal(String id, String tab, String title, String description, int order,
                                       boolean completed, double current, double target, String unit, String displayUnit) {
        return new ViewModel.Goal("camp_checklist:" + id, tab, title, description, "", order,
                completed, false, current, target, target == 0 ? 0 : current / target,
                unit, displayUnit, false, "");
    }
}
