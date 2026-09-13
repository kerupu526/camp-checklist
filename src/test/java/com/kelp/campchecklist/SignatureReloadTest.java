package com.kelp.campchecklist;

import com.kelp.campchecklist.api.progress.ConditionResult;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignatureReloadTest {
    @Test void semanticReloadPreservesCompletionAndToastButStartsFreshState() {
        var store = new ProgressStore();
        var entry = store.entry("test:goal");
        entry.completed = true;
        entry.toastShown = true;
        entry.activeSignature = "old";
        entry.counter("old").current = 73;
        entry.counter("old").state.putString("addon:data", "kept");
        assertTrue(ChecklistRuntime.activateSignature(entry, "new"));
        assertTrue(entry.completed);
        assertTrue(entry.toastShown);
        assertEquals(0, entry.counter("new").current);
        assertTrue(entry.counter("new").state.isEmpty());
        var restored = ProgressStore.load(store.save()).entry("test:goal");
        assertTrue(restored.completed);
        assertTrue(restored.toastShown);
        assertEquals("new", restored.activeSignature);
        assertEquals(73, restored.counter("old").current);
        assertEquals("kept", restored.counter("old").state.getString("addon:data"));
    }

    @Test void identicalSignatureKeepsStateAndDoesNotDirtyAgain() {
        var entry = new ProgressStore.Entry();
        assertTrue(ChecklistRuntime.activateSignature(entry, "same"));
        entry.counter("same").current = 12;
        assertFalse(ChecklistRuntime.activateSignature(entry, "same"));
        assertEquals(12, entry.counter("same").current);
    }

    @Test void switchingBackDoesNotResurrectOldNumericState() {
        var entry = new ProgressStore.Entry();
        ChecklistRuntime.activateSignature(entry, "a");
        entry.counter("a").current = 9;
        ChecklistRuntime.activateSignature(entry, "b");
        ChecklistRuntime.activateSignature(entry, "a");
        assertEquals(0, entry.counter("a").current);
    }

    @Test void nativeCompletionIsStickyAcrossUnsatisfiedAndUnavailableResults() {
        var entry = new ProgressStore.Entry();
        assertTrue(ChecklistRuntime.stickyComplete(entry, ConditionResult.booleanResult(true)));
        entry.completed = true;
        assertFalse(ChecklistRuntime.stickyComplete(entry, ConditionResult.booleanResult(false)));
        assertFalse(ChecklistRuntime.stickyComplete(entry, ConditionResult.unavailable(Component.literal("missing"))));
        assertTrue(entry.completed);
    }
}
