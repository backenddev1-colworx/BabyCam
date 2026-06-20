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
                CONTROL_PARENT_CAMERA,
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
            parentCamera = true,
            parentTalk = true,
            videoSaver = true,
        ).failSafeOff()

        assertFalse(result.camera)
        assertFalse(result.microphone)
        assertFalse(result.torch)
        assertFalse(result.cryDetection)
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
}
