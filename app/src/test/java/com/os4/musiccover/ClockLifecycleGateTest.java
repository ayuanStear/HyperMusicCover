package com.os4.musiccover;

import org.junit.Test;

import static org.junit.Assert.*;

public class ClockLifecycleGateTest {
    @Test public void wakeInvalidatesASleepHandoffAlreadyQueuedOnTheMainThread() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        long queuedSleep = gate.onSleepStarted();
        assertTrue(gate.isCurrentSleep(queuedSleep));
        assertTrue(gate.isSleeping());

        long wake = gate.onWakeStarted();

        assertFalse(gate.isCurrentSleep(queuedSleep));
        assertTrue(gate.isCurrentWake(wake));
        assertFalse(gate.isSleeping());
    }

    @Test public void aLaterSleepGetsItsOwnCurrentToken() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        long firstSleep = gate.onSleepStarted();
        gate.onWakeStarted();
        long nextSleep = gate.onSleepStarted();

        assertFalse(gate.isCurrentSleep(firstSleep));
        assertTrue(gate.isCurrentSleep(nextSleep));
        assertTrue(gate.isSleeping());
    }

    @Test public void aNewSleepInvalidatesAWakeHandoffStillQueuedOnTheMainThread() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        long queuedWake = gate.onWakeStarted();

        long sleep = gate.onSleepStarted();

        assertFalse(gate.isCurrentWake(queuedWake));
        assertTrue(gate.isCurrentSleep(sleep));
    }

    @Test public void lateSleepHandoffIsRejectedAfterANewerWake() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        gate.onSleepStarted();
        long wake = gate.onWakeStarted();

        assertEquals(0L, gate.onScreenOff(true));
        assertTrue(gate.isCurrentWake(wake));
    }

    @Test public void fallbackSleepHandoffStartsWhenNoWakeIsPending() {
        ClockLifecycleGate gate = new ClockLifecycleGate();

        long sleep = gate.onScreenOff(false);
        assertTrue(sleep != 0L);
        assertTrue(gate.isSleeping());
        gate.onScreenOnBroadcast();
        assertTrue(gate.onScreenOff(false) != 0L);
        assertTrue(gate.isSleeping());
    }

    @Test public void hookOwnedCycleRejectsAnOffBroadcastAfterTheWakeCompleted() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        gate.onSleepStarted();
        gate.onWakeStarted();
        gate.onScreenOnBroadcast();

        assertEquals(0L, gate.onScreenOff(true));
    }

    @Test public void wakeCannotInterleaveWithAnAdmittedSleepAction() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        long sleep = gate.onSleepStarted();
        final boolean[] ran = {false};

        assertTrue(gate.runIfCurrentSleep(sleep, () -> ran[0] = true));
        assertTrue(ran[0]);
        gate.onWakeStarted();
        ran[0] = false;
        assertFalse(gate.runIfCurrentSleep(sleep, () -> ran[0] = true));
        assertFalse(ran[0]);
    }

    @Test public void screenOnDoesNotEraseWakeBeforeTheFirstFrame() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        long wake = gate.onWakeStarted();

        gate.onScreenOnBroadcast();

        assertTrue(gate.isCurrentWake(wake));
        assertEquals(0L, gate.onScreenOff(true));
        gate.acknowledgeWake(wake);
        assertFalse(gate.isCurrentWake(wake));
    }

    @Test public void noHookScreenOffCanSupersedeAnUnconsumedWake() {
        ClockLifecycleGate gate = new ClockLifecycleGate();
        gate.onWakeStarted();

        assertTrue(gate.onScreenOff(false) != 0L);
        assertTrue(gate.isSleeping());
    }
}
