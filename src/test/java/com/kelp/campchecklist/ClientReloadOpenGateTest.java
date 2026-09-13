package com.kelp.campchecklist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientReloadOpenGateTest {
    @Test
    void defersAndCoalescesOpenUntilOneClientTickAfterReloadApply() {
        ClientReloadOpenGate gate = new ClientReloadOpenGate();

        assertTrue(gate.requestOpen());

        long generation = gate.beginReload();
        assertFalse(gate.requestOpen());
        assertFalse(gate.requestOpen());
        gate.markReloadComplete(generation);

        // The reload apply callback only arms the release; the next client tick sends it.
        assertFalse(gate.requestOpen());
        assertFalse(gate.finishClientTick(false));
        assertTrue(gate.finishClientTick(true));
        assertFalse(gate.finishClientTick(true));
        assertTrue(gate.requestOpen());
    }

    @Test
    void aNewReloadKeepsADeferredRequestGated() {
        ClientReloadOpenGate gate = new ClientReloadOpenGate();

        long firstGeneration = gate.beginReload();
        gate.requestOpen();
        gate.markReloadComplete(firstGeneration);
        long secondGeneration = gate.beginReload();
        assertFalse(gate.finishClientTick(true));
        gate.markReloadComplete(secondGeneration);
        assertTrue(gate.finishClientTick(true));
    }

    @Test
    void resetDropsPendingOpenForLogout() {
        ClientReloadOpenGate gate = new ClientReloadOpenGate();

        gate.beginReload();
        gate.requestOpen();
        gate.reset();

        assertFalse(gate.finishClientTick(true));
        assertTrue(gate.requestOpen());
    }
}
