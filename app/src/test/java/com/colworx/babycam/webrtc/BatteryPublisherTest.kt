package com.colworx.babycam.webrtc

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryPublisherTest {

    @Test
    fun firstValidObservation_publishesPercentage() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)

        publisher.observe(level = 25, scale = 50)

        assertEquals(listOf(50), published)
    }

    @Test
    fun percentage_isClampedToValidRange() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)

        publisher.observe(level = 150, scale = 100)

        assertEquals(listOf(100), published)
    }

    @Test
    fun invalidObservations_areIgnored() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)

        publisher.observe(level = -1, scale = 100)
        publisher.observe(level = 50, scale = 0)
        publisher.observe(level = 50, scale = -1)

        assertEquals(emptyList<Int>(), published)
    }

    @Test
    fun unchangedObservation_isSuppressed() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)

        publisher.observe(level = 50, scale = 100)
        publisher.observe(level = 1, scale = 2)

        assertEquals(listOf(50), published)
    }

    @Test
    fun changedObservation_isPublished() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)

        publisher.observe(level = 50, scale = 100)
        publisher.observe(level = 49, scale = 100)

        assertEquals(listOf(50, 49), published)
    }

    @Test
    fun forceCurrent_withoutKnownObservation_doesNothing() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)

        publisher.forceCurrent()

        assertEquals(emptyList<Int>(), published)
    }

    @Test
    fun forceCurrent_withKnownObservation_publishesOnceEvenWhenUnchanged() {
        val published = mutableListOf<Int>()
        val publisher = BatteryPublisher(published::add)
        publisher.observe(level = 50, scale = 100)

        publisher.forceCurrent()

        assertEquals(listOf(50, 50), published)
    }
}
