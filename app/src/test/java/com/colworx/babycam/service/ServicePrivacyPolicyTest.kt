package com.colworx.babycam.service

import com.colworx.babycam.ui.canRestoreSavedSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePrivacyPolicyTest {

    @Test
    fun autoStart_defaultsOff() {
        assertFalse(AUTO_START_DEFAULT)
    }

    @Test
    fun bootResume_requiresBootActionAndExplicitOptIn() {
        assertFalse(
            shouldOfferBootResume(
                action = "other",
                monitoringEnabled = true,
                autoStartEnabled = true,
            ),
        )
        assertFalse(
            shouldOfferBootResume(
                action = BOOT_COMPLETED_ACTION,
                monitoringEnabled = false,
                autoStartEnabled = true,
            ),
        )
        assertFalse(
            shouldOfferBootResume(
                action = BOOT_COMPLETED_ACTION,
                monitoringEnabled = true,
                autoStartEnabled = false,
            ),
        )
        assertTrue(
            shouldOfferBootResume(
                action = BOOT_COMPLETED_ACTION,
                monitoringEnabled = true,
                autoStartEnabled = true,
            ),
        )
    }

    @Test
    fun savedSessionRestoration_waitsForLockResolutionAndUnlock() {
        assertFalse(canRestoreSavedSession(lockEnabled = null, unlocked = false))
        assertFalse(canRestoreSavedSession(lockEnabled = true, unlocked = false))
        assertTrue(canRestoreSavedSession(lockEnabled = true, unlocked = true))
        assertTrue(canRestoreSavedSession(lockEnabled = false, unlocked = false))
    }

    @Test
    fun parentStandbyNotification_mentionsAudioAndAlerts() {
        assertTrue(PARENT_STANDBY_NOTIFICATION_TEXT.contains("audio", ignoreCase = true))
        assertTrue(PARENT_STANDBY_NOTIFICATION_TEXT.contains("alerts", ignoreCase = true))
    }
}
