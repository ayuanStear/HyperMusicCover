package com.os4.musiccover

import java.util.concurrent.atomic.AtomicLong

/**
 * Identity of one music-card entry.  The number is deliberately independent of the spring
 * response: a fast spring must not make an older frame callback look like the current entry.
 */
internal data class MusicTransitionToken(val id: Long, val scene: Boolean)

/** Keeps the rendered spring progress continuous when a low-response spring overshoots. */
internal class TransitionProgress(initial: Float, toNative: Boolean) {
    private var toNative = toNative
    private var last = initial.coerceIn(0f, 1f)

    fun retarget(toNative: Boolean, current: Float) {
        this.toNative = toNative
        last = current.coerceIn(0f, 1f)
    }

    fun sample(raw: Float, interactive: Boolean = false): Float {
        val bounded = raw.coerceIn(0f, 1f)
        if (interactive) {
            last = bounded
        } else if (toNative) {
            last = maxOf(last, bounded)
        } else {
            last = minOf(last, bounded)
        }
        return last
    }
}

/**
 * Small, main-thread-owned fence for the asynchronous music-card hand-off.
 *
 * Scene entry owns the card until that entry finishes.  A notification refresh may still update
 * its data, but it cannot turn the card's morph back into the pill.  Only an explicit scene
 * request (the user's pull-down/scene exit) may change that direction.  Finishing an old token is
 * a no-op, which keeps a late onSettled callback from clearing a newer entry.
 */
internal class MusicTransitionOwnership {
    companion object {
        private val nextId = AtomicLong(0L)
    }

    var active: MusicTransitionToken? = null
        private set
    private var sceneOwned = false

    fun startEntry(scene: Boolean): MusicTransitionToken {
        val token = MusicTransitionToken(nextId.incrementAndGet(), scene)
        active = token
        sceneOwned = scene
        return token
    }

    fun owns(token: MusicTransitionToken?): Boolean = token != null && active?.id == token.id

    /** Whether a request may mutate the currently owned transition. */
    fun accepts(token: MusicTransitionToken?, toNative: Boolean, scene: Boolean): Boolean {
        if (!owns(token)) return false
        // Scene entry/exit is an explicit owner hand-off.  Dynamic notification refreshes are
        // not: they must not reverse an entry while its clock/card exchange is still running.
        if (scene) return true
        return !(sceneOwned && !toNative)
    }

    fun promoteToScene(token: MusicTransitionToken?): Boolean {
        if (!owns(token)) return false
        sceneOwned = true
        return true
    }

    fun finish(token: MusicTransitionToken?) {
        if (owns(token)) {
            active = null
            sceneOwned = false
        }
    }
}
