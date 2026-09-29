package com.os4.musiccover

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTransitionTokenTest {
    @Test fun everyMusicEntryGetsItsOwnToken() {
        val owner = MusicTransitionOwnership()

        val first = owner.startEntry(scene = true)
        owner.finish(first)
        val second = owner.startEntry(scene = true)

        assertNotEquals(first.id, second.id)
        assertFalse(owner.owns(first))
        assertTrue(owner.owns(second))
    }

    @Test fun staleCallbacksCannotFinishOrReverseTheCurrentEntry() {
        val owner = MusicTransitionOwnership()
        val first = owner.startEntry(scene = true)
        val current = owner.startEntry(scene = true)

        owner.finish(first)

        assertTrue(owner.owns(current))
        assertFalse(owner.accepts(first, toNative = false, scene = false))
        assertTrue(owner.accepts(current, toNative = true, scene = true))
    }

    @Test fun notificationSelectionCannotReverseAnActiveSceneEntry() {
        val owner = MusicTransitionOwnership()
        val token = owner.startEntry(scene = true)

        assertFalse(owner.accepts(token, toNative = false, scene = false))
        assertTrue(owner.accepts(token, toNative = false, scene = true))
        assertSame(token, owner.active)
    }

    @Test fun responseSettingDoesNotChangeOwnershipRules() {
        val owner = MusicTransitionOwnership()
        val token = owner.startEntry(scene = true)

        listOf(0.2968638f, 0.37f, 0.38f, 0.52f).forEach { response ->
            assertTrue("response=$response", owner.accepts(token, toNative = true, scene = true))
            assertFalse("response=$response", owner.accepts(token, toNative = false, scene = false))
        }
    }

    @Test fun enteringProgressDoesNotBacktrackAfterAnOvershoot() {
        val progress = TransitionProgress(0f, toNative = true)

        assertEquals(0f, progress.sample(0f), 0f)
        assertEquals(0.8f, progress.sample(0.8f), 0f)
        assertEquals(1f, progress.sample(1.08f), 0f)
        assertEquals(1f, progress.sample(0.94f), 0f)
    }

    @Test fun reversingKeepsTheCurrentPoseAndThenMovesMonotonicallyHome() {
        val progress = TransitionProgress(0f, toNative = true)
        progress.sample(0.72f)
        progress.retarget(toNative = false, current = 0.72f)

        assertEquals(0.62f, progress.sample(0.62f), 0f)
        assertEquals(0.4f, progress.sample(0.4f), 0f)
        assertEquals(0f, progress.sample(-0.1f), 0f)
    }
}
