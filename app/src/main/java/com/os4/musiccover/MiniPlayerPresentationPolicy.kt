// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 btm_m
package com.os4.musiccover

import java.util.WeakHashMap

internal data class MiniPlayerPresentationInput(
    val enabled: Boolean,
    val sessionUsable: Boolean,
    val nativeRequested: Boolean,
    val keyguardOwned: Boolean,
    val sceneVisible: Boolean,
    val nativeSceneOverride: Boolean = false,
    val transitionActive: Boolean = false,
    val controlCenterOpen: Boolean = false,
)

internal data class MiniPlayerPresentation(
    val showMini: Boolean,
    val suppressNative: Boolean,
)

/**
 * A media session can briefly report STATE_NONE while a player replaces its session (for
 * example, while advancing to the next track).  The runtime keeps that same controller alive
 * for a short grace period; during that period it must still count as the music island's source.
 */
internal fun sessionUsableDuringGrace(
    usable: Boolean,
    sessionToken: Any?,
    pendingEndToken: Any?,
): Boolean = usable || sessionToken != null && sessionToken == pendingEndToken

/** Keeps scene visibility separate from the user's selected media presentation. */
internal object MiniPlayerPresentationPolicy {
    fun evaluate(input: MiniPlayerPresentationInput): MiniPlayerPresentation {
        val available = input.enabled && input.sessionUsable
        val miniSelected = !input.nativeRequested
        val lockscreenSurfaceVisible = input.sceneVisible || input.controlCenterOpen
        return MiniPlayerPresentation(
            showMini = available &&
                (input.transitionActive || miniSelected && lockscreenSurfaceVisible),
            suppressNative = available && miniSelected && input.keyguardOwned && lockscreenSurfaceVisible &&
                !input.nativeSceneOverride && !input.transitionActive,
        )
    }
}

/**
 * Dynamic-mode choice for the current media app.
 *
 * A player can destroy and recreate its MediaSession while advancing its queue. The user's
 * card/pill choice is independent of that implementation detail, so retain it across the gap
 * and restore it when the same package publishes its replacement session.
 */
internal class MiniPlayerSessionSelection {
    private var sessionToken: Any? = null
    private var sessionPackage: String? = null
    /** Known package expected if the current replacement token has not exposed one yet. */
    private var provisionalPackage: String? = null
    private var nativeRequested = false
    private var retainedPackage: String? = null
    private var retainedNativeRequested = false

    fun observe(token: Any): Boolean = observe(token,
        if (sessionToken == token) sessionPackage.orEmpty() else "")

    /** A player may recreate its session while advancing a queue; keep its explicit choice. */
    fun observe(token: Any, packageName: String): Boolean {
        if (sessionToken == token) {
            // MediaController can expose the token before its package name is populated. Learn
            // the package on the next callback. A replacement inherits the old choice only
            // provisionally until this confirms that it belongs to the same player.
            if (sessionPackage.isNullOrEmpty() && packageName.isNotEmpty()) {
                if (nativeRequested && provisionalPackage != null && provisionalPackage != packageName) {
                    nativeRequested = false
                }
                sessionPackage = packageName
                provisionalPackage = null
            }
            return false
        }
        val expectedPackage = when {
            sessionToken != null && nativeRequested -> sessionPackage
            sessionToken == null && retainedNativeRequested -> retainedPackage
            else -> null
        }?.takeIf { it.isNotEmpty() }
        val keepChoice = expectedPackage != null &&
            (packageName.isEmpty() || packageName == expectedPackage)
        sessionToken = token
        sessionPackage = packageName
        nativeRequested = keepChoice
        provisionalPackage = expectedPackage.takeIf { keepChoice && packageName.isEmpty() }
        retainedPackage = null
        retainedNativeRequested = false
        return true
    }

    fun requestNative(token: Any): Boolean {
        val sessionChanged = observe(token)
        val changed = !nativeRequested
        nativeRequested = true
        // An explicit selection on this token supersedes provisional inheritance.
        provisionalPackage = null
        return sessionChanged || changed
    }

    fun requestMini(token: Any): Boolean {
        val sessionChanged = observe(token)
        val changed = nativeRequested
        nativeRequested = false
        provisionalPackage = null
        return sessionChanged || changed
    }

    fun resetChoice(): Boolean {
        val changed = nativeRequested || retainedPackage != null && retainedNativeRequested
        nativeRequested = false
        provisionalPackage = null
        retainedPackage = null
        retainedNativeRequested = false
        return changed
    }

    fun end(token: Any): Boolean {
        if (sessionToken != token) return false
        retainedPackage = provisionalPackage ?: sessionPackage
        retainedNativeRequested = nativeRequested
        sessionToken = null
        sessionPackage = null
        provisionalPackage = null
        nativeRequested = false
        return true
    }

    fun nativeRequestedFor(token: Any?): Boolean =
        token != null && sessionToken == token && nativeRequested
}

internal data class ViewPresentationState(
    val visibility: Int,
    val alpha: Float,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/** Stores both properties because alpha alone can lose a frame to an OEM property animator. */
internal class WeakViewOverrideRegistry<T : Any> {
    private val originals = WeakHashMap<T, ViewPresentationState>()

    val hasOverrides: Boolean
        get() = originals.isNotEmpty()

    fun suppress(target: T, visibility: Int, alpha: Float,
                 translationX: Float = 0f, translationY: Float = 0f,
                 scaleX: Float = 1f, scaleY: Float = 1f,
                 apply: (ViewPresentationState) -> Unit) {
        if (!originals.containsKey(target)) {
            originals[target] = ViewPresentationState(
                visibility, alpha, translationX, translationY, scaleX, scaleY,
            )
        }
        apply(ViewPresentationState(
            4, 0f, translationX, translationY, scaleX, scaleY,
        ))
    }

    fun restore(apply: (T, ViewPresentationState) -> Unit) {
        val snapshot = originals.entries.map { it.key to it.value }
        originals.clear()
        snapshot.forEach { (target, original) -> apply(target, original) }
    }
}
