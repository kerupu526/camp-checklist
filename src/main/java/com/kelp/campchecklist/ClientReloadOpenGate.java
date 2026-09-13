package com.kelp.campchecklist;

/**
 * Small synchronized gate that coalesces a checklist-open request across a resource reload.
 *
 * <p>The reload listener marks completion first, then the client tick releases the gate. This
 * leaves one full client tick between the last reload apply callback and the deferred open, so
 * shared LDLib2 font/resource state is no longer being rebuilt when the server receives the
 * request.</p>
 */
final class ClientReloadOpenGate {
    private boolean reloadActive;
    private boolean releaseOnNextTick;
    private boolean openPending;
    private long reloadGeneration;
    private long completedGeneration;

    synchronized long beginReload() {
        reloadGeneration++;
        reloadActive = true;
        releaseOnNextTick = false;
        return reloadGeneration;
    }

    synchronized void markReloadComplete(long generation) {
        if (generation == reloadGeneration) releaseOnNextTick = true;
    }

    /** Returns whether the caller may send the request immediately. */
    synchronized boolean requestOpen() {
        if (reloadActive || releaseOnNextTick) {
            openPending = true;
            return false;
        }
        return true;
    }

    /** Returns whether a reload or its post-apply guard still blocks UI work. */
    synchronized boolean isBlocked() {
        return reloadActive || releaseOnNextTick;
    }

    /** Ends the post-reload guard only after the client reports that its reload overlay is gone. */
    synchronized boolean finishClientTick(boolean resourceReloadReady) {
        if (!releaseOnNextTick || !resourceReloadReady) return false;
        releaseOnNextTick = false;
        reloadActive = false;
        completedGeneration = reloadGeneration;
        boolean requested = openPending;
        openPending = false;
        return requested;
    }

    synchronized long generation() {
        return reloadGeneration;
    }

    synchronized long completedGeneration() {
        return completedGeneration;
    }

    synchronized void reset() {
        reloadActive = false;
        releaseOnNextTick = false;
        openPending = false;
        reloadGeneration = 0;
        completedGeneration = 0;
    }
}
