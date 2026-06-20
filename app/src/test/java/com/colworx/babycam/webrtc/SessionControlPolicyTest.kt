package com.colworx.babycam.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControlPolicyTest {
    @Test
    fun defaults_areAllOff() {
        assertEquals(SessionControlState(), SessionControlState())
    }

    @Test
    fun replayCommands_includeEveryRemoteControlExactlyOnce() {
        val commands = SessionControlState(
            camera = true,
            microphone = true,
            torch = true,
            cryDetection = true,
            lullaby = true,
            parentCamera = true,
            parentTalk = true,
            videoSaver = true,
        ).commands()

        assertEquals(
            listOf(
                CONTROL_CAMERA,
                CONTROL_MICROPHONE,
                CONTROL_TORCH,
                CONTROL_CRY,
                CONTROL_LULLABY,
                CONTROL_PARENT_CAMERA,
                CONTROL_PARENT_TALK,
                CONTROL_VIDEO_SAVER,
            ),
            commands.map { it.first },
        )
        assertTrue(commands.all { it.second })
    }

    @Test
    fun failSafe_turnsCapabilitiesOffButKeepsQualityPreference() {
        val result = SessionControlState(
            camera = true,
            microphone = true,
            torch = true,
            cryDetection = true,
            lullaby = true,
            parentCamera = true,
            parentTalk = true,
            videoSaver = true,
        ).failSafeOff()

        assertFalse(result.camera)
        assertFalse(result.microphone)
        assertFalse(result.torch)
        assertFalse(result.cryDetection)
        assertFalse(result.lullaby)
        assertFalse(result.parentCamera)
        assertFalse(result.parentTalk)
        assertTrue(result.videoSaver)
    }

    @Test
    fun syncGate_acceptsOneActionPerCorrelatedSyncId() {
        val gate = SessionSyncGate()

        assertTrue(gate.accept("sync-1"))
        assertFalse(gate.accept("sync-1"))
        assertTrue(gate.accept("sync-2"))
        assertEquals("sync-2", gate.currentId())
    }

    @Test
    fun revisionGate_rejectsOlderCommandsForTheSameControl() {
        val gate = ControlRevisionGate()

        assertTrue(gate.accept(CONTROL_CAMERA, 4))
        assertFalse(gate.accept(CONTROL_CAMERA, 3))
        assertFalse(gate.accept(CONTROL_CAMERA, 4))
        assertTrue(gate.accept(CONTROL_MICROPHONE, 1))
        assertTrue(gate.accept(CONTROL_CAMERA, 5))
    }

    @Test
    fun commandTracker_acceptsOnlyTheLatestAcknowledgement() {
        val tracker = ParentCommandTracker()
        val first = tracker.next(CONTROL_CAMERA)
        val second = tracker.next(CONTROL_CAMERA)

        assertFalse(tracker.acceptAck(CONTROL_CAMERA, first))
        assertTrue(tracker.acceptAck(CONTROL_CAMERA, second))
        assertFalse(tracker.acceptAck(CONTROL_CAMERA, second))
    }

    @Test
    fun syncTracker_retriesUntilExactSyncIsAcknowledged() {
        val tracker = ParentSyncTracker()

        tracker.begin("sync-1")
        assertTrue(tracker.shouldRetry("sync-1"))
        assertFalse(tracker.acknowledge("sync-old"))
        assertTrue(tracker.shouldRetry("sync-1"))
        assertTrue(tracker.acknowledge("sync-1"))
        assertFalse(tracker.shouldRetry("sync-1"))
    }

    @Test
    fun cameraActualState_usesTransitionResultForOnAndOff() {
        assertTrue(cameraActualState(requestedOn = true, transitionSucceeded = true))
        assertFalse(cameraActualState(requestedOn = true, transitionSucceeded = false))
        assertFalse(cameraActualState(requestedOn = false, transitionSucceeded = false))
        assertFalse(cameraActualState(requestedOn = false, transitionSucceeded = true))
    }
}
