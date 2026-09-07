package com.kelp.campchecklist;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabViewTopologyTest {
    @Test
    void detectsRenderedChildrenMissingFromRegistrationMapAndClearRemovesThem() {
        TabView tabView = new TabView();
        Tab registeredHeader = new Tab();
        registeredHeader.setId("registered");
        UIElement registeredContent = new UIElement().setId("registered-content");
        tabView.addTab(registeredHeader, registeredContent);

        Tab orphanHeader = new Tab();
        orphanHeader.setId("orphan");
        UIElement orphanContent = new UIElement().setId("orphan-content");
        tabView.tabScroller.addScrollViewChild(orphanHeader);
        tabView.tabContentContainer.addChild(orphanContent);

        TabViewTopology.Audit dirty = TabViewTopology.inspect(tabView);
        assertFalse(dirty.isConsistent());
        assertEquals(2, dirty.headers().size());
        assertEquals(2, dirty.contents().size());
        assertEquals(1, dirty.registered().size());
        assertEquals(orphanHeader, dirty.orphanHeaders().getFirst());
        assertEquals(orphanContent, dirty.orphanContents().getFirst());

        tabView.clear();
        TabViewTopology.Audit clean = TabViewTopology.inspect(tabView);
        assertTrue(clean.isConsistent());
        assertTrue(clean.headers().isEmpty());
        assertTrue(clean.contents().isEmpty());
        assertTrue(clean.registered().isEmpty());
    }
}
