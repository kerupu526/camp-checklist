package com.kelp.campchecklist;

import java.util.Locale;

/** Pure title matching used to preserve the selected tab across a topology rebuild. */
final class TabBindingPlanner {
    private TabBindingPlanner() {}

    static boolean sameTitle(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
