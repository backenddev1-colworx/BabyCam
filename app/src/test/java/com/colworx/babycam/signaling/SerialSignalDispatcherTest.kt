package com.colworx.babycam.signaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SerialSignalDispatcherTest {
    @Test
    fun dispatch_returnsWithoutWaitingForPublishAndPreservesOrder() {
        val releaseFirst = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val published = Collections.synchronizedList(mutableListOf<Int>())
        val dispatcher = SerialSignalDispatcher()

        val startedAt = System.nanoTime()
        dispatcher.dispatch {
            releaseFirst.await(2, TimeUnit.SECONDS)
            published += 1
            completed.countDown()
        }
        dispatcher.dispatch {
            published += 2
            completed.countDown()
        }
        val dispatchElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue(dispatchElapsedMs < 500)
        releaseFirst.countDown()
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), published)
        dispatcher.close()
    }
}
