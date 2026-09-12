package com.parlor.designsystem.components

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParlorToastConcurrencyTest {
    @Test
    fun concurrent_producers_cannot_reuse_lifetime_ids_or_lose_a_non_overflowing_wave() {
        val state = ParlorToastState()
        val executor = Executors.newFixedThreadPool(4)
        val identities = mutableSetOf<Long>()
        try {
            repeat(50) { wave ->
                val barrier = CyclicBarrier(4)
                val expected = (0 until 4).map { "wave-$wave-producer-$it" }.toSet()
                val futures = expected.map { message ->
                    executor.submit(Callable {
                        barrier.await(5, TimeUnit.SECONDS)
                        state.show(message)
                    })
                }
                futures.forEach { it.get(10, TimeUnit.SECONDS) }
                val queue = state.toasts.value
                assertEquals(expected, queue.map { it.text }.toSet())
                assertEquals(4, queue.size)
                queue.forEach { toast ->
                    assertTrue(identities.add(toast.id), "Each lifetime must have a never-reused ID")
                    state.dismiss(toast.id)
                }
                assertTrue(state.toasts.value.isEmpty())
            }
            assertEquals(200, identities.size)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Test-owned workers must stop")
        }
    }

    @Test
    fun concurrent_identical_messages_still_coalesce_and_floods_remain_bounded() {
        val state = ParlorToastState()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val barrier = CyclicBarrier(4)
            val same = List(4) {
                executor.submit(Callable {
                    barrier.await(5, TimeUnit.SECONDS)
                    repeat(50) { state.show("Same notification") }
                })
            }
            same.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf("Same notification"), state.toasts.value.map { it.text })
            val firstId = state.toasts.value.single().id
            val flood = List(4) { producer ->
                executor.submit(Callable {
                    repeat(50) { index -> state.show("producer-$producer-message-$index") }
                })
            }
            flood.forEach { it.get(10, TimeUnit.SECONDS) }
            state.dismiss(firstId)
            assertEquals(4, state.toasts.value.size)
            assertEquals(4, state.toasts.value.map { it.id }.distinct().size)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Test-owned workers must stop")
        }
    }
}
