package com.colworx.babycam.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun enabledUnlockedApp_relocksWhenBackgrounded() {
        assertTrue(shouldRelock(lockEnabled = true, unlocked = true))
    }

    @Test
    fun disabledOrAlreadyLockedApp_needsNoRelockMutation() {
        assertFalse(shouldRelock(lockEnabled = false, unlocked = true))
        assertFalse(shouldRelock(lockEnabled = true, unlocked = false))
    }
}
