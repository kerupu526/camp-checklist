package com.kelp.campchecklist;

import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.UIResource;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.function.BiConsumer;

/** LDLib2 Menu UI backed by the existing checklist ViewModel and Network. */
public final class ChecklistUi {
    public static final ResourceLocation MENU_ID = ResourceLocation.fromNamespaceAndPath(CampChecklist.ID, "checklist");
    private static final String TEMPLATE_PATH = "pack(camp_checklist:resources/ui/checklist_ui.ui.nbt)";
    /** Weak keys let a closed ModularUI be collected without requiring a client-only lifecycle hook. */
    private static final Set<Instance> CLIENT_INSTANCES = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final Map<MinecraftServer, Instance> PREWARMED_SERVERS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static volatile ViewModel clientModel = ViewModel.EMPTY;
    private static volatile Instance prewarmedClient;

    private ChecklistUi() {}

    public static void registerMenu() {
        PlayerUIMenuType.register(MENU_ID, player -> new PlayerUIMenuType.PlayerUIHolder() {
            @Override
            public ModularUI createUI(Player holderPlayer) {
                return create(holderPlayer);
            }
        });
    }

    public static void open(ServerPlayer player) {
        Network.send(player, CampChecklist.runtime(player.server).view());
        PlayerUIMenuType.openUI(player, MENU_ID);
    }

    /** Builds the first server-side UI instance during world startup, before a key press can request it. */
    public static void prewarmServer(MinecraftServer server) {
        long start = System.nanoTime();
        Instance warmup = new Instance(false);
        warmup.refresh(CampChecklist.runtime(server).view());
        PREWARMED_SERVERS.put(server, warmup);
        long elapsed = System.nanoTime() - start;
        CampChecklist.LOGGER.info("Checklist server UI prewarm: loaded={}, elapsed={}ms",
                warmup.templateLoaded, elapsed / 1_000_000.0);
    }

    /** Builds the first client-side UI instance when the login snapshot arrives. */
    public static void prewarmClient(ViewModel model) {
        if (prewarmedClient != null) return;
        long start = System.nanoTime();
        Instance warmup = new Instance(true);
        warmup.refresh(model);
        prewarmedClient = warmup;
        CampChecklist.LOGGER.info("Checklist client UI prewarm: loaded={}, elapsed={}ms",
                warmup.templateLoaded, (System.nanoTime() - start) / 1_000_000.0);
    }

    private static ModularUI create(Player player) {
        long modelStart = System.nanoTime();
        ViewModel model = player.level().isClientSide
                ? clientModel
                : CampChecklist.runtime(((ServerPlayer) player).server).view();
        long modelEnd = System.nanoTime();
        long templateStart = System.nanoTime();
        Instance instance;
        if (player.level().isClientSide) {
            instance = prewarmedClient;
            prewarmedClient = null;
        } else {
            instance = PREWARMED_SERVERS.remove(((ServerPlayer) player).server);
        }
        boolean reusedPrewarm = instance != null;
        if (instance == null) instance = new Instance(player.level().isClientSide);
        long templateEnd = System.nanoTime();
        long refreshStart = System.nanoTime();
        instance.refresh(model);
        long refreshEnd = System.nanoTime();
        CampChecklist.LOGGER.info(
                "Checklist {} create timing: prewarmed={}, viewModel={}ms, template={}ms, initialRefresh={}ms",
                player.level().isClientSide ? "client" : "server",
                reusedPrewarm,
                (modelEnd - modelStart) / 1_000_000.0,
                (templateEnd - templateStart) / 1_000_000.0,
                (refreshEnd - refreshStart) / 1_000_000.0);
        if (player.level().isClientSide) CLIENT_INSTANCES.add(instance);
        return ModularUI.of(instance.ui, player);
    }

    /** Refreshes open client menus using the existing core snapshot. */
    public static void setClientModel(ViewModel model) {
        clientModel = model;
        if (prewarmedClient != null) prewarmedClient.refresh(model);
        for (Instance instance : List.copyOf(CLIENT_INSTANCES)) instance.refresh(model);
    }

    public static void clearClientInstances() {
        CLIENT_INSTANCES.clear();
        prewarmedClient = null;
        clientModel = ViewModel.EMPTY;
    }

    public static void clearServer(MinecraftServer server) {
        PREWARMED_SERVERS.remove(server);
    }

    private static UITemplate loadTemplate() {
        return UIResource.INSTANCE.getResourceInstance().getResource(IResourcePath.parse(TEMPLATE_PATH));
    }

    /** Package-private seam for exercising the real authored-template binding in JUnit. */
    static TemplateTestFixture renderTemplateForTest(UITemplate template, ViewModel model, boolean clientSide) {
        return renderTemplateForTest(template, model, clientSide, Network::requestManual);
    }

    static TemplateTestFixture renderTemplateForTest(UITemplate template, ViewModel model, boolean clientSide,
                                                       BiConsumer<String, Boolean> manualRequest) {
        Instance instance = new Instance(template, clientSide, manualRequest);
        instance.refresh(model);
        return new TemplateTestFixture(instance);
    }

    static final class TemplateTestFixture {
        private final Instance instance;

        private TemplateTestFixture(Instance instance) {
            this.instance = instance;
        }

        UI ui() {
            return instance.ui;
        }

        void refresh(ViewModel model) {
            instance.refresh(model);
        }

        void setDetailsExpanded(String goalId, boolean expanded) {
            instance.setDetailsExpanded(goalId, expanded);
        }

        boolean isDetailsExpanded(String goalId) {
            return instance.expandedGoals.getOrDefault(goalId, false);
        }

        int dragBoundScrollerCount() {
            return instance.dragBoundScrollers.size();
        }
    }

    private static final class Instance {
        private final boolean clientSide;
        private final boolean templateLoaded;
        private final UI ui;
        private final UIElement root;
        private final Map<String, Tab> headerPrototypes;
        private final Map<String, UIElement> contentPrototypes;
        private final Map<String, ScrollerView> cardLists;
        private final Map<String, UIElement> cardPrototypes;
        private final TabView tabView;
        private final Map<String, TemplateTab> boundTabs = new LinkedHashMap<>();
        private final Map<String, Map<String, UIElement>> renderedCards = new LinkedHashMap<>();
        private final Map<String, List<String>> renderedGoalOrder = new LinkedHashMap<>();
        private final Map<String, Boolean> expandedGoals = new HashMap<>();
        private final Map<String, Boolean> editableGoals = new HashMap<>();
        private final Map<String, UIElement> detailPrototypes = new HashMap<>();
        private final Map<String, List<String>> renderedDetailOrder = new HashMap<>();
        private final Map<String, List<DetailRow>> detailRows = new HashMap<>();
        private final Set<ScrollerView> dragBoundScrollers = Collections.newSetFromMap(new IdentityHashMap<>());
        private final BiConsumer<String, Boolean> manualRequest;
        private ScrollerView draggingScroller;
        private float dragLastY;
        private float dragStartY;
        private boolean dragMoved;
        private boolean suppressNextCardClick;
        private List<String> boundOrder = List.of();
        private boolean tabsBound;
        private boolean initialTimingLogged;

        private Instance(boolean clientSide) {
            this(loadTemplate(), clientSide, Network::requestManual);
        }

        private Instance(UITemplate template, boolean clientSide, BiConsumer<String, Boolean> manualRequest) {
            this.clientSide = clientSide;
            this.manualRequest = manualRequest;
            this.templateLoaded = template != null;
            this.ui = template == null ? UI.of(new UIElement()) : template.createUI();
            this.root = ui.getRootElement();
            if (template == null) {
                CampChecklist.LOGGER.error("Checklist UI template could not be loaded from {}", TEMPLATE_PATH);
            } else {
                CampChecklist.LOGGER.debug("Checklist {} template styles: builtin={}, stylesheets={}",
                        clientSide ? "client" : "server",
                        template.getBuiltinStyles(), template.getStylesheets());
            }
            this.tabView = root.selectId("tab-view", TabView.class).findFirst().orElse(null);
            TabViewTopology.Audit audit = tabView == null ? null : TabViewTopology.inspect(tabView);
            if (audit != null) logTemplateAudit(audit);

            Map<String, Tab> capturedHeaders = new LinkedHashMap<>();
            if (audit != null) {
                audit.headers().stream().sorted(Comparator.comparingInt(Tab::getSiblingIndex))
                        .forEach(header -> capturedHeaders.put(authoredKey(header.getId()), header));
            }
            this.headerPrototypes = Map.copyOf(capturedHeaders);

            Map<String, UIElement> capturedContents = new LinkedHashMap<>();
            captureContentPrototype(capturedContents, "create", "create-content");
            captureContentPrototype(capturedContents, "mekanism", "mekanism-content");
            captureContentPrototype(capturedContents, "ae2", "ae2-content");
            captureContentPrototype(capturedContents, "pneumaticcraft", "pnc-content");
            this.contentPrototypes = Map.copyOf(capturedContents);

            UIElement createContent = capturedContents.get("create");
            if (createContent == null && audit != null && !audit.contents().isEmpty()) {
                createContent = audit.contents().getFirst();
            }
            UIElement cardContainerPrototype = createContent == null
                    ? null
                    : createContent.selectId("card-container").findFirst().orElse(null);
            if (cardContainerPrototype != null) {
                for (Map.Entry<String, UIElement> entry : capturedContents.entrySet()) {
                    boolean alreadyHasScroller = entry.getValue().selfAndAllChildren()
                            .anyMatch(ScrollerView.class::isInstance);
                    if (entry.getKey().equals("create") || alreadyHasScroller) continue;
                    // The editor-authored non-Create roots are placeholders. Preserve each root's
                    // own layout, but replace its preview label with an independent copy of the
                    // authored card container. UIElement.copy() is a codec round-trip in 2.2.38.a
                    // and retains the nested ScrollerView and card subtree.
                    entry.getValue().clearAllChildren();
                    entry.getValue().addChild(cardContainerPrototype.copy());
                }
            }

            // Keep each template-owned content root as the live object that TabView will reattach.
            Map<String, ScrollerView> capturedCardLists = new LinkedHashMap<>();
            for (Map.Entry<String, UIElement> entry : capturedContents.entrySet()) {
                ScrollerView list = findCardList(entry.getValue(), entry.getKey());
                if (list != null) capturedCardLists.put(entry.getKey(), list);
            }
            this.cardLists = Map.copyOf(capturedCardLists);

            Map<String, UIElement> capturedCards = new LinkedHashMap<>();
            for (Map.Entry<String, UIElement> entry : capturedContents.entrySet()) {
                UIElement card = findCardPrototype(entry.getValue(), entry.getKey());
                if (card != null) capturedCards.put(entry.getKey(), card);
            }
            this.cardPrototypes = Map.copyOf(capturedCards);
        }

        private void refresh(ViewModel model) {
            if (tabView == null) return;
            long refreshStart = System.nanoTime();
            // Ignore the template's stale serialized selection on the first bind. It belongs to
            // the inconsistent three-entry registration list, not the visible four-tab order.
            String selectedId = !tabsBound || tabView.getSelectedTab() == null
                    ? null : tabView.getSelectedTab().getId();
            String selectedTitle = !tabsBound || tabView.getSelectedTab() == null
                    ? "" : tabView.getSelectedTab().text.getText().getString();
            Map<String, ViewModel.Tab> modelTabs = new LinkedHashMap<>();
            model.tabs().stream()
                    .sorted(Comparator.comparingInt(ViewModel.Tab::order).thenComparing(ViewModel.Tab::id))
                    .forEach(tab -> modelTabs.put(tab.id(), tab));

            long available = model.goals().stream().filter(goal -> !goal.unavailable()).count();
            long completed = model.goals().stream().filter(goal -> !goal.unavailable() && goal.completed()).count();
            root.selectId("progression-count", Label.class).findFirst()
                    .ifPresent(label -> label.setText(Component.literal(completed + " / " + available)));

            bindTabs(modelTabs.values().stream().toList());
            long topologyEnd = System.nanoTime();
            for (ViewModel.Tab tab : modelTabs.values()) {
                TemplateTab template = boundTabs.get(tab.id());
                if (template == null) continue;
                template.tab().setId(tab.id());
                template.tab().setText(Component.literal(tab.title()));
                populateTab(template.content(), tab, model.goals());
            }
            long cardsEnd = System.nanoTime();

            if (!modelTabs.isEmpty()) {
                String desired = selectedId != null && modelTabs.containsKey(selectedId) ? selectedId : modelTabs.values().stream()
                        .filter(tab -> TabBindingPlanner.sameTitle(tab.title(), selectedTitle))
                        .map(ViewModel.Tab::id)
                        .findFirst()
                        .orElseGet(() -> modelTabs.keySet().iterator().next());
                TemplateTab selected = boundTabs.get(desired);
                if (selected != null) tabView.selectTab(selected.tab());
            }
            if (!initialTimingLogged) {
                CampChecklist.LOGGER.info(
                        "Checklist {} initial UI timing: topology={}ms, cards={}ms, total={}ms",
                        clientSide ? "client" : "server",
                        (topologyEnd - refreshStart) / 1_000_000.0,
                        (cardsEnd - topologyEnd) / 1_000_000.0,
                        (System.nanoTime() - refreshStart) / 1_000_000.0);
                initialTimingLogged = true;
            }
        }

        private void bindTabs(List<ViewModel.Tab> modelTabs) {
            List<String> desiredOrder = modelTabs.stream().map(ViewModel.Tab::id).toList();
            if (tabsBound && boundOrder.equals(desiredOrder)) return;

            // The editor-authored file currently renders four pairs but serializes only three in
            // TabView's registration list. Normalize that topology exactly once per UI instance;
            // later snapshots keep these objects and only refresh their bound values.
            tabView.clear();
            boundTabs.clear();
            for (int index = 0; index < modelTabs.size(); index++) {
                ViewModel.Tab modelTab = modelTabs.get(index);
                TemplateTab added = addTemplateTab(modelTab, index);
                if (added != null) boundTabs.put(modelTab.id(), added);
            }
            boundOrder = desiredOrder;
            tabsBound = true;

            if (!boundTabs.isEmpty()) tabView.selectTab(boundTabs.values().iterator().next().tab());
            logRuntimeAudit();
        }

        private TemplateTab addTemplateTab(ViewModel.Tab tab, int index) {
            Tab headerPrototype = headerPrototypes.get(authoredKey(tab.id()));
            if (headerPrototype == null) {
                CampChecklist.LOGGER.error("Checklist {} tab {} has no authored header prototype; refusing runtime UI fallback",
                        clientSide ? "client" : "server", tab.id());
                return null;
            }
            UIElement content = contentPrototypeFor(tab.id());
            if (content == null) {
                CampChecklist.LOGGER.error("Checklist {} tab {} has no authored content subtree; refusing runtime UI fallback",
                        clientSide ? "client" : "server", tab.id());
                return null;
            }
            Tab header = (Tab) headerPrototype.copy();
            header.setId(tab.id());
            header.setText(Component.literal(tab.title()));
            // Do not copy the content root: the template-owned ScrollerView, viewport and
            // scrollbar are part of this live subtree. addTab() re-parents it after clear().
            content.setId(tab.id() + "-content");
            tabView.addTab(header, content, index);
            return new TemplateTab(header, content);
        }

        private void populateTab(UIElement tabContent, ViewModel.Tab tab, List<ViewModel.Goal> allGoals) {
            List<ViewModel.Goal> goals = allGoals.stream()
                    .filter(goal -> goal.tab().equals(tab.id()))
                    .sorted(Comparator.comparingInt(ViewModel.Goal::order).thenComparing(ViewModel.Goal::id))
                    .toList();
            UIElement headerTitle = tabContent.selectId("header-title").findFirst().orElse(null);
            setFirstLabel(headerTitle, tab.title());
            setSecondLabel(headerTitle, tab.description());

            long available = goals.stream().filter(goal -> !goal.unavailable()).count();
            long completed = goals.stream().filter(goal -> !goal.unavailable() && goal.completed()).count();
            setFirstLabel(tabContent.selectId("card-progression").findFirst().orElse(null), completed + " / " + available);

            ScrollerView cardList = cardLists.get(prototypeKey(tab.id()));
            if (cardList == null) cardList = findCardList(tabContent, tab.id());
            if (cardList == null) {
                CampChecklist.LOGGER.warn("Checklist {} tab {} has no card-list ScrollerView", clientSide ? "client" : "server", tab.id());
                return;
            }
            // LDLib2 Scroller already implements scrollbar dragging. Keep the template's
            // visual style, while ensuring the authored scrollbar remains hit-testable after
            // the content subtree has been reparented into this TabView.
            cardList.verticalScroller.scrollBar.setActive(true).setAllowHitTest(true);
            installDragScrolling(cardList);
            UIElement prototype = cardPrototypeFor(tab.id());
            if (prototype == null) {
                CampChecklist.LOGGER.warn("Checklist {} tab {} has no authored goal-card prototype",
                        clientSide ? "client" : "server", tab.id());
                return;
            }

            String key = prototypeKey(tab.id());
            List<String> goalOrder = goals.stream().map(ViewModel.Goal::id).toList();
            Map<String, UIElement> cards = renderedCards.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            if (!goalOrder.equals(renderedGoalOrder.get(key))) {
                cardList.clearAllScrollViewChildren();
                for (String removedGoalId : cards.keySet()) clearGoalBindings(removedGoalId);
                cards.clear();
                for (ViewModel.Goal goal : goals) {
                    // Keep the editor-authored card only as an immutable prototype. Every runtime
                    // goal receives its own subtree so no card can be re-parented between goals.
                    UIElement card = createCard(goal, prototype);
                    cards.put(goal.id(), card);
                    cardList.addScrollViewChild(card);
                }
                renderedGoalOrder.put(key, goalOrder);
            } else {
                // Network syncs and manual toggles keep the authored card instances and only
                // update their bound values. This avoids repeated codec-based UIElement.copy().
                for (ViewModel.Goal goal : goals) {
                    UIElement card = cards.get(goal.id());
                    if (card != null) updateCard(card, goal);
                }
            }
            int renderedCards = cardList.viewContainer.getChildren().size();
            CampChecklist.LOGGER.debug("Checklist {} tab {} goals={}, cards={}, scrollerIdentity={}",
                    clientSide ? "client" : "server", tab.id(), goals.size(), renderedCards,
                    Integer.toHexString(System.identityHashCode(cardList)));
            if (renderedCards != goals.size()) {
                CampChecklist.LOGGER.warn(
                        "Checklist {} tab {} card population mismatch: expected={}, rendered={}, prototype={}[id={},children={}]",
                        clientSide ? "client" : "server", tab.id(), goals.size(), renderedCards,
                        prototype.getClass().getSimpleName(), prototype.getId(), prototype.getChildren().size());
            }
        }

        private UIElement createCard(ViewModel.Goal goal, UIElement prototype) {
            UIElement card = prototype.copy().setId("goal-" + goal.id().replace(':', '_'));
            buildDetails(card, goal);
            installCardInteractions(card, goal);
            updateCard(card, goal);
            return card;
        }

        private void updateCard(UIElement card, ViewModel.Goal goal) {
            Label title = card.selectId("goal-title", Label.class).findFirst().orElse(null);
            Label description = card.selectId("goal-description", Label.class).findFirst().orElse(null);
            if (title != null) title.setText(Component.literal(goal.title()));
            if (description != null) {
                // Description is presentation data owned by the JSON definition. Do not
                // prefix, suffix, or reconstruct it from type/checker/parameters.
                description.setText(Component.literal(goal.description()));
                if (!initialTimingLogged) {
                    CampChecklist.LOGGER.debug(
                            "Checklist description {} width={} contentWidth={} wrap={} adaptiveWidth={} adaptiveHeight={} text={}",
                            goal.id(), description.getSizeWidth(), description.getContentWidth(),
                            description.getTextStyle().textWrap(), description.getTextStyle().adaptiveWidth(),
                            description.getTextStyle().adaptiveHeight(), goal.description());
                }
            }

            Toggle toggle = card.selectId("goal-toggle", Toggle.class).findFirst().orElse(null);
            if (toggle != null) {
                toggle.setOn(goal.completed(), false);
                boolean editable = clientSide && goal.manual() && !goal.unavailable();
                editableGoals.put(goal.id(), editable);
                if (editable) {
                    toggle.setActive(true);
                } else {
                    toggle.disabled();
                }
            }

            ProgressBar progress = card.selectId("goal-progress-bar", ProgressBar.class).findFirst().orElse(null);
            Label progressText = card.selectId("goal-progress-text", Label.class).findFirst().orElse(null);
            if (progress != null) progress.setProgress((float) Math.max(0, Math.min(1, goal.progress())));
            if (progressText != null) progressText.setText(Component.literal(formatProgress(goal)));
            reconcileDetails(card, goal);
            updateDetails(card, goal);
        }

        private void buildDetails(UIElement card, ViewModel.Goal goal) {
            UIElement details = card.selectId("goal-details").findFirst().orElse(null);
            UIElement prototype = details == null ? null : details.selectId("goal-detail-prototype").findFirst().orElse(null);
            if (details == null || prototype == null) return;
            prototype.removeSelf();
            detailPrototypes.put(goal.id(), prototype);
            rebuildDetails(details, goal);
        }

        private void reconcileDetails(UIElement card, ViewModel.Goal goal) {
            List<String> desiredOrder = goal.details().stream().map(ViewModel.Detail::id).toList();
            if (desiredOrder.equals(renderedDetailOrder.get(goal.id()))) return;
            UIElement details = card.selectId("goal-details").findFirst().orElse(null);
            if (details != null) rebuildDetails(details, goal);
        }

        private void rebuildDetails(UIElement details, ViewModel.Goal goal) {
            for (DetailRow oldRow : detailRows.getOrDefault(goal.id(), List.of())) oldRow.element().removeSelf();
            UIElement prototype = detailPrototypes.get(goal.id());
            List<DetailRow> rows = new ArrayList<>();
            if (prototype != null) for (ViewModel.Detail detail : goal.details()) {
                UIElement row = prototype.copy();
                ItemSlot icon = row.selectId("detail-icon", ItemSlot.class).findFirst().orElse(null);
                Label name = row.selectId("detail-name", Label.class).findFirst().orElse(null);
                Label progress = row.selectId("detail-progress", Label.class).findFirst().orElse(null);
                DetailPresentation presentation = detailPresentation(detail);
                if (name != null) {
                    name.setText(presentation.name());
                    name.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                            event.hoverTooltips = HoverTooltips.empty().append(name.getText()));
                }
                details.addChild(row);
                rows.add(new DetailRow(row, icon, name, progress));
            }
            detailRows.put(goal.id(), rows);
            renderedDetailOrder.put(goal.id(), goal.details().stream().map(ViewModel.Detail::id).toList());
            if (rows.isEmpty()) expandedGoals.remove(goal.id());
            details.setDisplay(!goal.details().isEmpty() && expandedGoals.getOrDefault(goal.id(), false));
        }

        private void updateDetails(UIElement card, ViewModel.Goal goal) {
            UIElement details = card.selectId("goal-details").findFirst().orElse(null);
            if (details == null) return;
            List<DetailRow> rows = detailRows.getOrDefault(goal.id(), List.of());
            for (int i = 0; i < Math.min(rows.size(), goal.details().size()); i++) {
                ViewModel.Detail detail = goal.details().get(i);
                DetailRow row = rows.get(i);
                DetailPresentation presentation = detailPresentation(detail);
                if (row.icon() != null) row.icon().setItem(presentation.stack());
                if (row.name() != null) row.name().setText(presentation.name());
                if (row.progress() != null) row.progress().setText(Component.literal(formatDetailProgress(detail)));
                CampChecklist.LOGGER.debug(
                        "Checklist detail {} item={} display={} rowIdentity={} nameWidth={} progressWidth={} progress={}",
                        goal.id(), detail.id(), presentation.name(), Integer.toHexString(System.identityHashCode(row)),
                        row.name() == null ? -1 : row.name().getContentWidth(),
                        row.progress() == null ? -1 : row.progress().getContentWidth(), formatDetailProgress(detail));
            }
            details.setDisplay(!goal.details().isEmpty() && expandedGoals.getOrDefault(goal.id(), false));
        }

        private void installCardInteractions(UIElement card, ViewModel.Goal goal) {
            Toggle toggle = card.selectId("goal-toggle", Toggle.class).findFirst().orElse(null);
            if (toggle != null) {
                toggle.stopInteractionEventsPropagation();
                toggle.setOnToggleChanged(value -> {
                    if (editableGoals.getOrDefault(goal.id(), false)) manualRequest.accept(goal.id(), value);
                });
            }
            card.addEventListener(UIEvents.CLICK, event -> {
                if (suppressNextCardClick) {
                    suppressNextCardClick = false;
                    event.stopImmediatePropagation();
                    return;
                }
                if (detailRows.getOrDefault(goal.id(), List.of()).isEmpty()) return;
                boolean expanded = !expandedGoals.getOrDefault(goal.id(), false);
                setDetailsExpanded(card, goal.id(), expanded);
                event.stopPropagation();
            });
        }

        private void setDetailsExpanded(String goalId, boolean expanded) {
            for (Map<String, UIElement> cards : renderedCards.values()) {
                UIElement card = cards.get(goalId);
                if (card != null) {
                    setDetailsExpanded(card, goalId, expanded);
                    return;
                }
            }
        }

        private void setDetailsExpanded(UIElement card, String goalId, boolean expanded) {
            UIElement details = card.selectId("goal-details").findFirst().orElse(null);
            if (details == null || detailRows.getOrDefault(goalId, List.of()).isEmpty()) return;
            expandedGoals.put(goalId, expanded);
            details.setDisplay(expanded);
        }

        private void clearGoalBindings(String goalId) {
            editableGoals.remove(goalId);
            expandedGoals.remove(goalId);
            detailPrototypes.remove(goalId);
            renderedDetailOrder.remove(goalId);
            detailRows.remove(goalId);
        }

        private void installDragScrolling(ScrollerView list) {
            if (!dragBoundScrollers.add(list)) return;
            list.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                suppressNextCardClick = false;
                if (event.button != 0 || isScrollControl(event.target, list)) return;
                draggingScroller = list;
                dragStartY = dragLastY = event.y;
                dragMoved = false;
            }, true);
            list.addEventListener(UIEvents.MOUSE_MOVE, event -> {
                if (draggingScroller != list) return;
                float delta = event.y - dragLastY;
                dragLastY = event.y;
                if (!dragMoved && Math.abs(event.y - dragStartY) < 4) return;
                dragMoved = true;
                suppressNextCardClick = true;
                list.verticalScroller.scrollValue(-delta);
                event.stopPropagation();
            }, true);
            list.addEventListener(UIEvents.MOUSE_UP, event -> {
                if (draggingScroller == list) {
                    if (dragMoved) suppressNextCardClick = true;
                    draggingScroller = null;
                    dragMoved = false;
                }
            }, true);
            list.addEventListener(UIEvents.MOUSE_LEAVE, event -> {
                if (draggingScroller == list) {
                    draggingScroller = null;
                    dragMoved = false;
                }
            }, true);
        }

        private static boolean isScrollControl(UIElement target, ScrollerView list) {
            UIElement current = target;
            while (current != null && current != list) {
                if (current == list.verticalScroller.scrollBar || current == list.verticalScroller.headButton || current == list.verticalScroller.tailButton)
                    return true;
                if (current instanceof Toggle) return true;
                current = current.getParent();
            }
            return false;
        }

        private static DetailPresentation detailPresentation(ViewModel.Detail detail) {
            ResourceLocation itemId = ResourceLocation.tryParse(detail.id());
            if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
                if (!stack.isEmpty()) return new DetailPresentation(stack, stack.getHoverName());
            }
            Component fallback = detail.label().startsWith("item.") || detail.label().startsWith("block.")
                    ? Component.translatable(detail.label()) : Component.literal(detail.label());
            return new DetailPresentation(ItemStack.EMPTY, fallback);
        }

        private static String formatDetailProgress(ViewModel.Detail detail) {
            String unit = detail.displayUnit();
            String suffix = switch (unit) {
                case "kilometers" -> " km";
                case "meters" -> " m";
                case "blocks" -> " blocks";
                default -> "";
            };
            return format(detail.displayCurrent()) + " / " + format(detail.displayTarget()) + suffix;
        }

        private void captureContentPrototype(Map<String, UIElement> target, String key, String elementId) {
            // Keep the editor-authored root as the live tab content so its root-level layout and
            // style classes remain the source of truth when the normalized pair is registered.
            root.selectId(elementId).findFirst().ifPresent(content -> {
                target.put(key, content);
                logContentShape(key, content);
            });
        }

        private ScrollerView findCardList(UIElement content, String tabId) {
            ScrollerView byId = content.selectId("card-list", ScrollerView.class).findFirst().orElse(null);
            if (byId != null) return byId;

            UIElement named = content.selectId("card-list").findFirst().orElse(null);
            if (named instanceof ScrollerView scroller) {
                CampChecklist.LOGGER.debug("Checklist {} tab {} resolved card-list by untyped id", clientSide ? "client" : "server", tabId);
                return scroller;
            }
            if (named != null) {
                ScrollerView nested = named.selfAndAllChildren().filter(ScrollerView.class::isInstance)
                        .map(ScrollerView.class::cast).findFirst().orElse(null);
                if (nested != null) {
                    CampChecklist.LOGGER.debug("Checklist {} tab {} resolved ScrollerView nested below card-list element type {}",
                            clientSide ? "client" : "server", tabId, named.getClass().getSimpleName());
                    return nested;
                }
            }

            CampChecklist.LOGGER.warn("Checklist {} tab {} has no ScrollerView below authored card-list, contentShape={}",
                    clientSide ? "client" : "server", tabId, describeContent(content));
            return null;
        }

        private UIElement findCardPrototype(UIElement content, String key) {
            UIElement byId = content.selectId("first-card").findFirst().orElse(null);
            if (byId != null) return byId;

            ScrollerView list = cardLists == null ? findCardList(content, key) : cardLists.get(prototypeKey(key));
            if (list != null && !list.viewContainer.getChildren().isEmpty()) {
                UIElement first = list.viewContainer.getChildren().getFirst();
                CampChecklist.LOGGER.debug("Checklist {} content {} resolved first-card from ScrollerView child: type={}, id={}, children={}",
                        clientSide ? "client" : "server", key, first.getClass().getSimpleName(), first.getId(), first.getChildren().size());
                return first;
            }
            CampChecklist.LOGGER.warn("Checklist {} content {} has no first-card prototype: shape={}",
                    clientSide ? "client" : "server", key, describeContent(content));
            return null;
        }

        private void logContentShape(String key, UIElement content) {
            List<ScrollerView> scrollers = content.selfAndAllChildren().filter(ScrollerView.class::isInstance)
                    .map(ScrollerView.class::cast).toList();
            UIElement named = content.selectId("card-list").findFirst().orElse(null);
            CampChecklist.LOGGER.debug("Checklist {} raw content {}: card-list-id-type={}, scrollers={}, shape={}",
                    clientSide ? "client" : "server", key,
                    named == null ? "<missing>" : named.getClass().getSimpleName(), scrollers.size(), describeContent(content));
        }

        private String describeContent(UIElement content) {
            return content.selfAndAllChildren().limit(96)
                    .map(element -> element.getClass().getSimpleName() + "#" + element.getId())
                    .toList().toString();
        }

        private UIElement contentPrototypeFor(String tabId) {
            return contentPrototypes.get(prototypeKey(tabId));
        }

        private UIElement cardPrototypeFor(String tabId) {
            return cardPrototypes.get(prototypeKey(tabId));
        }

        private static String prototypeKey(String tabId) {
            int separator = tabId.indexOf(':');
            return separator < 0 ? tabId : tabId.substring(separator + 1);
        }

        private static String authoredKey(String tabId) {
            String key = prototypeKey(tabId);
            return key.equals("pnc") ? "pneumaticcraft" : key;
        }

        private void logTemplateAudit(TabViewTopology.Audit audit) {
            String scope = clientSide ? "client" : "server";
            if (audit.isConsistent()) {
                CampChecklist.LOGGER.info(
                        "Checklist {} template TabView is consistent: headers={}, contents={}, registered={}",
                        scope, audit.headers().size(), audit.contents().size(), audit.registered().size());
            } else {
                CampChecklist.LOGGER.warn(
                        "Checklist {} template TabView contains unregistered children: headers={}, contents={}, "
                                + "registered={}, orphanHeaders={}, orphanContents={}. Runtime topology will be rebuilt.",
                        scope, audit.headers().size(), audit.contents().size(), audit.registered().size(),
                        describeElements(audit.orphanHeaders()), describeElements(audit.orphanContents()));
            }
        }

        private void logRuntimeAudit() {
            TabViewTopology.Audit audit = TabViewTopology.inspect(tabView);
            if (audit.isConsistent()) {
                CampChecklist.LOGGER.info(
                        "Checklist {} runtime TabView rebuilt: headers={}, contents={}, registered={}",
                        clientSide ? "client" : "server",
                        audit.headers().size(), audit.contents().size(), audit.registered().size());
            } else {
                CampChecklist.LOGGER.error(
                        "Checklist {} runtime TabView is inconsistent after rebuild: headers={}, contents={}, "
                                + "registered={}, orphanHeaders={}, orphanContents={}",
                        clientSide ? "client" : "server",
                        audit.headers().size(), audit.contents().size(), audit.registered().size(),
                        describeElements(audit.orphanHeaders()), describeElements(audit.orphanContents()));
            }
        }

        private static String describeElements(List<? extends UIElement> elements) {
            return elements.stream()
                    .map(element -> "%s#%s[sibling=%d,identity=%x,displayed=%s]".formatted(
                            element.getClass().getSimpleName(), element.getId(), element.getSiblingIndex(),
                            System.identityHashCode(element), element.isDisplayed()))
                    .toList()
                    .toString();
        }

        private void setFirstLabel(UIElement parent, String text) {
            if (parent == null) return;
            parent.selfAndAllChildren().filter(Label.class::isInstance).map(Label.class::cast)
                    .findFirst().ifPresent(label -> label.setText(Component.literal(text)));
        }

        private void setSecondLabel(UIElement parent, String text) {
            if (parent == null) return;
            List<Label> labels = parent.selfAndAllChildren().filter(Label.class::isInstance)
                    .map(Label.class::cast).toList();
            if (labels.size() > 1) labels.get(1).setText(Component.literal(text));
        }

        private static String formatProgress(ViewModel.Goal goal) {
            String suffix = switch (goal.displayUnit()) {
                case "kilometers" -> " km";
                case "meters" -> " m";
                case "blocks" -> " blocks";
                default -> "";
            };
            return format(goal.displayCurrent()) + " / " + format(goal.displayTarget()) + suffix;
        }

        private static String format(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.000001) return Long.toString(Math.round(value));
            return String.format(Locale.ROOT, "%.1f", value);
        }

        private record TemplateTab(Tab tab, UIElement content) {}
        private record DetailPresentation(ItemStack stack, Component name) {}
        private record DetailRow(UIElement element, ItemSlot icon, Label name, Label progress) {}
    }
}
