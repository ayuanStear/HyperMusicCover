package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerPresentationPolicyTest {
    private fun presentation(
        nativeRequested: Boolean = false,
        keyguardOwned: Boolean = true,
        sceneVisible: Boolean = true,
        enabled: Boolean = true,
        sessionUsable: Boolean = true,
        nativeSceneOverride: Boolean = false,
        transitionActive: Boolean = false,
        controlCenterOpen: Boolean = false,
    ) = MiniPlayerPresentationPolicy.evaluate(
        MiniPlayerPresentationInput(
            enabled,
            sessionUsable,
            nativeRequested,
            keyguardOwned,
            sceneVisible,
            nativeSceneOverride,
            transitionActive,
            controlCenterOpen,
        ),
    )

    @Test fun dynamicNativeChoiceSurvivesUpdatesWithinTheSameSession() {
        val selection = MiniPlayerSessionSelection()
        val session = Any()
        selection.observe(session)
        selection.requestNative(session)

        // A track update observes the same MediaSession token and must not reset the choice.
        selection.observe(session)

        assertTrue(selection.nativeRequestedFor(session))
        assertEquals(MiniPlayerPresentation(false, false),
            presentation(nativeRequested = selection.nativeRequestedFor(session)))
    }

    @Test fun dynamicChoiceResetsForANewOrDestroyedSession() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val second = Any()
        selection.requestNative(first)

        selection.observe(second)
        assertFalse(selection.nativeRequestedFor(second))

        selection.requestNative(second)
        selection.end(second)
        assertFalse(selection.nativeRequestedFor(second))
    }

    @Test fun nativeChoiceSurvivesSessionRecreationBySamePackage() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val replacement = Any()

        selection.observe(first, "player")
        selection.requestNative(first)
        selection.observe(replacement, "player")

        assertTrue(selection.nativeRequestedFor(replacement))
    }

    @Test fun nativeChoiceSurvivesReplacementTokenUntilItsPackageArrives() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val replacement = Any()

        selection.observe(first, "player")
        selection.requestNative(first)
        selection.observe(replacement, "")

        // The replacement token is provisional while the framework has not filled its package.
        assertTrue(selection.nativeRequestedFor(replacement))
        selection.observe(replacement, "player")
        assertTrue(selection.nativeRequestedFor(replacement))
    }

    @Test fun provisionalNativeChoiceIsClearedWhenReplacementBelongsToAnotherPackage() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val replacement = Any()

        selection.observe(first, "player")
        selection.requestNative(first)
        selection.observe(replacement, "")
        selection.observe(replacement, "another.player")

        assertFalse(selection.nativeRequestedFor(replacement))
    }

    @Test fun sameSessionLearnsPackageNameWhenItArrivesLate() {
        val selection = MiniPlayerSessionSelection()
        val session = Any()

        // A MediaController token can be observed before the framework fills packageName.
        selection.observe(session, "")
        selection.requestNative(session)

        assertTrue(selection.nativeRequestedFor(session))
        // The later package callback should enrich the same token, not reset the choice.
        selection.observe(session, "player")
        assertTrue(selection.nativeRequestedFor(session))
    }

    @Test fun nativeChoiceSurvivesSessionEndAndReplacementBySamePackage() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val replacement = Any()

        selection.observe(first, "player")
        selection.requestNative(first)
        selection.end(first)
        selection.observe(replacement, "player")

        assertTrue(selection.nativeRequestedFor(replacement))
    }

    @Test fun miniChoiceAlsoSurvivesSessionEndWithoutTurningNative() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val replacement = Any()

        selection.observe(first, "player")
        selection.requestMini(first)
        selection.end(first)
        selection.observe(replacement, "player")

        assertFalse(selection.nativeRequestedFor(replacement))
    }

    @Test fun sessionRecreationByAnotherPackageResetsNativeChoice() {
        val selection = MiniPlayerSessionSelection()
        val first = Any()
        val replacement = Any()

        selection.observe(first, "player")
        selection.requestNative(first)
        selection.observe(replacement, "another.player")

        assertFalse(selection.nativeRequestedFor(replacement))
    }

    @Test fun dynamicChoiceSurvivesAControllerGapUntilTheSessionActuallyEnds() {
        val selection = MiniPlayerSessionSelection()
        val session = Any()

        selection.requestNative(session)
        // A track transition can temporarily make the controller unusable. The runtime's grace
        // period leaves the selection untouched during that gap.
        selection.observe(session)

        assertTrue(selection.nativeRequestedFor(session))
        selection.end(session)
        assertFalse(selection.nativeRequestedFor(session))
    }

    @Test fun sessionGraceKeepsTheSameTemporarilyUnavailableControllerPresent() {
        val session = Any()
        assertTrue(sessionUsableDuringGrace(false, session, session))
        assertTrue(sessionUsableDuringGrace(true, session, null))
        assertFalse(sessionUsableDuringGrace(false, session, null))
        assertFalse(sessionUsableDuringGrace(false, session, Any()))
    }

    @Test fun temporarySceneOcclusionNeverHidesBothPlayers() {
        val aodOrControlCenter = presentation(sceneVisible = false)

        assertFalse(aodOrControlCenter.showMini)
        assertFalse(aodOrControlCenter.suppressNative)
        assertEquals(MiniPlayerPresentation(true, true), presentation(sceneVisible = true))
    }

    @Test fun translucentControlCenterKeepsMiniSelectionAndNativeSuppression() {
        assertEquals(MiniPlayerPresentation(true, true), presentation(
            sceneVisible = false, controlCenterOpen = true))
        assertEquals(MiniPlayerPresentation(false, false), presentation(
            sceneVisible = false, controlCenterOpen = true, nativeRequested = true))
    }

    @Test fun nativeChoiceDisplaysOnlyTheVendorCard() {
        assertEquals(MiniPlayerPresentation(false, false), presentation(nativeRequested = true))
    }

    @Test fun coverAndLyricsTemporarilyGiveTheNativeCardOwnership() {
        assertEquals(MiniPlayerPresentation(false, false), presentation(
            sceneVisible = false,
            nativeSceneOverride = true,
        ))
    }

    @Test fun transitionKeepsBothShellsAvailableWithoutHardSuppression() {
        assertEquals(MiniPlayerPresentation(true, false), presentation(
            sceneVisible = false,
            nativeSceneOverride = true,
            transitionActive = true,
        ))
    }

    @Test fun leavingTheKeyguardRestoresNativeWithoutChangingTheChoice() {
        assertEquals(MiniPlayerPresentation(false, false),
            presentation(keyguardOwned = false, sceneVisible = false))
    }

    @Test fun disabledOrEndedSessionRestoresNative() {
        assertEquals(MiniPlayerPresentation(false, false), presentation(enabled = false))
        assertEquals(MiniPlayerPresentation(false, false), presentation(sessionUsable = false))
    }

    @Test fun visibilityAndAlphaAreRestoredForEveryHeaderGeneration() {
        class Header(var visibility: Int, var alpha: Float)
        val first = Header(0, 0.8f)
        val second = Header(0, 0.6f)
        val registry = WeakViewOverrideRegistry<Header>()

        fun suppress(header: Header) = registry.suppress(
            header, header.visibility, header.alpha,
        ) { state ->
            header.visibility = state.visibility
            header.alpha = state.alpha
        }
        suppress(first)
        suppress(second)
        assertEquals(4, first.visibility)
        assertEquals(0f, first.alpha, 0f)
        assertEquals(4, second.visibility)

        registry.restore { header, state ->
            header.visibility = state.visibility
            header.alpha = state.alpha
        }
        assertEquals(0, first.visibility)
        assertEquals(0.8f, first.alpha, 0f)
        assertEquals(0, second.visibility)
        assertEquals(0.6f, second.alpha, 0f)
    }
}
