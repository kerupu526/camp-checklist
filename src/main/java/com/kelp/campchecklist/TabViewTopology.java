package com.kelp.campchecklist;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Identity-based inspection of LDLib2's rendered TabView children and registration map. */
final class TabViewTopology {
    private TabViewTopology() {}

    static Audit inspect(TabView tabView) {
        List<Tab> headers = tabView.tabScroller.viewContainer.getChildren().stream()
                .filter(Tab.class::isInstance)
                .map(Tab.class::cast)
                .toList();
        List<UIElement> contents = List.copyOf(tabView.tabContentContainer.getChildren());
        Map<Tab, UIElement> registered = Map.copyOf(tabView.getTabContents());

        Set<Tab> registeredHeaders = Collections.newSetFromMap(new IdentityHashMap<>());
        registeredHeaders.addAll(registered.keySet());
        Set<UIElement> registeredContents = Collections.newSetFromMap(new IdentityHashMap<>());
        registeredContents.addAll(registered.values());

        return new Audit(
                headers,
                contents,
                registered,
                headers.stream().filter(header -> !registeredHeaders.contains(header)).toList(),
                contents.stream().filter(content -> !registeredContents.contains(content)).toList());
    }

    record Audit(
            List<Tab> headers,
            List<UIElement> contents,
            Map<Tab, UIElement> registered,
            List<Tab> orphanHeaders,
            List<UIElement> orphanContents
    ) {
        boolean isConsistent() {
            return orphanHeaders.isEmpty()
                    && orphanContents.isEmpty()
                    && headers.size() == registered.size()
                    && contents.size() == registered.size();
        }
    }
}
