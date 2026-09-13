package com.kelp.campchecklist;

import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiRetryPolicyTest {
    @Test
    void classifiesOnlyFontManagerConcurrentModification() {
        ConcurrentModificationException fontFailure = new ConcurrentModificationException();
        fontFailure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.util.HashMap", "computeIfAbsent", "HashMap.java", 1229),
                new StackTraceElement("com.lowdragmc.lowdraglib2.client.font.LDFontManager", "apply", "LDFontManager.java", 210)
        });
        ConcurrentModificationException unrelated = new ConcurrentModificationException();

        assertTrue(UiRetryPolicy.isTransientFontFailure(new RuntimeException(new RuntimeException(fontFailure))));
        assertFalse(UiRetryPolicy.isTransientFontFailure(unrelated));
        assertFalse(UiRetryPolicy.isTransientFontFailure(new IllegalStateException("unrelated")));
    }

    @Test
    void boundedRetryAllowsOneAttemptOnly() {
        UiRetryPolicy.BoundedRetry retry = new UiRetryPolicy.BoundedRetry();

        assertTrue(retry.schedule());
        assertFalse(retry.schedule());
        assertTrue(retry.take());
        assertFalse(retry.take());
        assertFalse(retry.schedule());
    }

    @Test
    void latestSnapshotReplacesPendingValueAndNewGenerationResetsBudget() {
        UiRetryPolicy.Latest<String> latest = new UiRetryPolicy.Latest<>();

        latest.hold("A", 1);
        latest.replace("B", 1);
        assertEquals("B", latest.takeWhenReady(1));
        assertTrue(latest.scheduleRetry());
        latest.replace("C", 1);
        assertEquals("C", latest.takeRetry(1));
        assertFalse(latest.scheduleRetry());

        latest.hold("D", 2);
        assertNull(latest.takeRetry(2));
        assertEquals("D", latest.takeWhenReady(2));
        assertFalse(latest.hasPending(2));
    }
}
