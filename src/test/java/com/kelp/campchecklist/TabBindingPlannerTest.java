package com.kelp.campchecklist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TabBindingPlannerTest {
    @Test
    void titleMatchingIgnoresPresentationPunctuationAndCase() {
        assertTrue(TabBindingPlanner.sameTitle("Pneumatic Craft", "pneumatic-craft"));
    }
}
