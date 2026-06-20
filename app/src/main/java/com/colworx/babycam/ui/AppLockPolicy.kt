package com.colworx.babycam.ui

fun shouldRelock(lockEnabled: Boolean, unlocked: Boolean): Boolean =
    lockEnabled && unlocked
