package com.os4.musiccover;

import java.util.concurrent.atomic.AtomicLong;

/** Prevents a queued sleep hand-off from running after a newer wake has started. */
final class ClockLifecycleGate {
    // Keep generation and state in one atomic value so observers cannot pair the generation
    // from one callback with the state from another.
    private static final long STATE_MASK = 3L;
    private static final long AWAKE = 0L;
    private static final long SLEEPING = 1L;
    private static final long WAKING = 2L;
    private final AtomicLong lifecycle = new AtomicLong();
    private final Object lock = new Object();

    long onSleepStarted() {
        synchronized (lock) {
            return advance(SLEEPING);
        }
    }

    long onWakeStarted() {
        synchronized (lock) {
            return advance(WAKING);
        }
    }

    boolean isCurrentSleep(long token) {
        long state = lifecycle.get();
        return generation(state) == token && isSleeping(state);
    }

    boolean isCurrentWake(long token) {
        long state = lifecycle.get();
        return generation(state) == token && isWaking(state);
    }

    boolean isSleeping() {
        return isSleeping(lifecycle.get());
    }

    long onSleepHandoff() {
        synchronized (lock) {
            long state = lifecycle.get();
            if (isSleeping(state)) return generation(state);
            // A broadcast can arrive after the binder already started a newer wake.
            if (isWaking(state)) return 0L;
            return advance(SLEEPING);
        }
    }

    long onScreenOff(boolean earlySleepHookAvailable) {
        synchronized (lock) {
            long state = lifecycle.get();
            if (isSleeping(state)) return generation(state);
            // With no early hook, this broadcast is the only sleep signal and may supersede an
            // unconsumed OEM-only wake. A hook-owned wake must win over its delayed broadcast.
            if (isWaking(state)) return earlySleepHookAvailable ? 0L : advance(SLEEPING);
            // When the early hook exists, an AWAKE state means this off broadcast belongs to an
            // already completed cycle. Only the hook can open the next sleep epoch.
            if (earlySleepHookAvailable) return 0L;
            return advance(SLEEPING);
        }
    }

    /** Admit the side effect while holding the same lock used by wake publication. */
    boolean runIfCurrentSleep(long token, Runnable action) {
        synchronized (lock) {
            long state = lifecycle.get();
            if (token == 0L || generation(state) != token || !isSleeping(state)) return false;
            action.run();
            return true;
        }
    }

    long onScreenOnBroadcast() {
        synchronized (lock) {
            // Keep a binder wake alive until the first lock-screen frame consumes it. The
            // SCREEN_ON broadcast can precede that frame and must not erase the latch.
            long state = lifecycle.get();
            if (isWaking(state)) return generation(state);
            return advance(AWAKE);
        }
    }

    void acknowledgeWake(long token) {
        synchronized (lock) {
            long state = lifecycle.get();
            if (generation(state) == token && isWaking(state)) advance(AWAKE);
        }
    }

    private long advance(long mode) {
        long state = lifecycle.updateAndGet(current ->
                ((generation(current) + 1L) << 2) | mode);
        return generation(state);
    }

    private static long generation(long state) {
        return state >>> 2;
    }

    private static boolean isSleeping(long state) {
        return (state & STATE_MASK) == SLEEPING;
    }

    private static boolean isWaking(long state) {
        return (state & STATE_MASK) == WAKING;
    }
}
