package com.kelp.campchecklist;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Set;

/** Internal containment policy for the known LDLib2 font-cache race. */
final class UiRetryPolicy {
    private static final String LD_FONT_MANAGER = "com.lowdragmc.lowdraglib2.client.font.LDFontManager";

    private UiRetryPolicy() {}

    static boolean isTransientFontFailure(Throwable failure) {
        boolean concurrentModification = false;
        boolean fontManagerFrame = false;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof ConcurrentModificationException) concurrentModification = true;
            for (StackTraceElement frame : current.getStackTrace()) {
                if (LD_FONT_MANAGER.equals(frame.getClassName()) && "apply".equals(frame.getMethodName())) {
                    fontManagerFrame = true;
                    break;
                }
            }
        }
        return concurrentModification && fontManagerFrame;
    }

    /** One-shot retry state. It never schedules a second retry for the same operation. */
    static final class BoundedRetry {
        private boolean scheduled;
        private boolean consumed;

        boolean schedule() {
            if (scheduled || consumed) return false;
            scheduled = true;
            return true;
        }

        boolean take() {
            if (!scheduled) return false;
            scheduled = false;
            consumed = true;
            return true;
        }

        boolean scheduled() {
            return scheduled;
        }

        void reset() {
            scheduled = false;
            consumed = false;
        }
    }

    /** Latest-wins slot used while a decoded client snapshot waits for a safe UI tick. */
    static final class Latest<T> {
        private T value;
        private long generation = Long.MIN_VALUE;
        private boolean waitingForReady;
        private final BoundedRetry retry = new BoundedRetry();

        void hold(T value, long generation) {
            resetForGeneration(generation);
            this.value = value;
            waitingForReady = true;
        }

        void replace(T value, long generation) {
            resetForGeneration(generation);
            this.value = value;
            if (!waitingForReady && !retry.scheduled()) waitingForReady = true;
        }

        T takeWhenReady(long generation) {
            if (this.generation != generation || !waitingForReady) return null;
            waitingForReady = false;
            return value;
        }

        boolean scheduleRetry() {
            waitingForReady = false;
            return retry.schedule();
        }

        T takeRetry(long generation) {
            if (this.generation != generation || !retry.take()) return null;
            return value;
        }

        boolean hasPending(long generation) {
            return this.generation == generation && (waitingForReady || retry.scheduled());
        }

        void clear() {
            value = null;
            generation = Long.MIN_VALUE;
            waitingForReady = false;
            retry.reset();
        }

        private void resetForGeneration(long generation) {
            if (this.generation == generation) return;
            clear();
            this.generation = generation;
        }
    }
}
