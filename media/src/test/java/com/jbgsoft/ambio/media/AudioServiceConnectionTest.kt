package com.jbgsoft.ambio.media

import androidx.media3.session.MediaController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * AudioServiceConnection binds to a real service, so these tests replace only the one
 * thing that needs a device — how the controller future is built — and drive that
 * future by hand.
 *
 * What is being pinned down is that a connection attempt only ever writes the field
 * while it is still the attempt the class holds. The failure mode is not a crash: a
 * stale callback quietly clears a live future, nothing is left holding the controller,
 * and nothing calls connect() again, so the app is silent until the process dies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioServiceConnectionTest {

    /**
     * A future whose completion and whose listener dispatch are two separate steps.
     *
     * For a real future observed from the thread that completes it they are one step,
     * but AudioServiceConnection registers with directExecutor(), so its callback runs
     * on whichever thread finished the connection. Splitting them is how a
     * single-threaded test reproduces a callback that is still in flight while the main
     * thread has already torn the connection down and started another one.
     */
    private class ManualFuture : ListenableFuture<MediaController> {
        private val pending = mutableListOf<Pair<Runnable, Executor>>()
        private var value: MediaController? = null
        private var failure: Throwable? = null
        private var done = false

        /** Resolves the future without running its listeners. */
        fun completeWith(controller: MediaController) {
            value = controller
            done = true
        }

        /** Fails the future without running its listeners. */
        fun failWith(cause: Throwable) {
            failure = cause
            done = true
        }

        /** Runs the listeners that a real future would have run on completion. */
        fun dispatch() {
            val toRun = pending.toList()
            pending.clear()
            toRun.forEach { (listener, executor) -> executor.execute(listener) }
        }

        override fun addListener(listener: Runnable, executor: Executor) {
            pending += listener to executor
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = done

        override fun get(): MediaController {
            failure?.let { throw ExecutionException(it) }
            return checkNotNull(value) { "get() before the future was completed" }
        }

        override fun get(timeout: Long, unit: TimeUnit): MediaController = get()
    }

    private val futures = mutableListOf<ManualFuture>()
    private val listeners = mutableListOf<MediaController.Listener>()

    private fun connection(): AudioServiceConnection =
        AudioServiceConnection(ApplicationProvider.getApplicationContext()).apply {
            buildController = { listener ->
                listeners += listener
                ManualFuture().also { futures += it }
            }
        }

    @Test
    fun `a superseded attempt completing late does not clear the live connection`() {
        val connection = connection()
        val stale = mockk<MediaController>(relaxed = true)
        val live = mockk<MediaController>(relaxed = true)

        // The first attempt connects on another thread just as this one is torn down,
        // so releaseFuture's cancel loses and the callback is left in flight.
        connection.connect()
        futures[0].completeWith(stale)
        connection.disconnect()
        connection.connect()

        futures[0].dispatch()
        futures[1].completeWith(live)
        futures[1].dispatch()

        assertThat(connection.controller).isSameInstanceAs(live)
        assertThat(connection.isConnected.value).isTrue()
    }

    @Test
    fun `onDisconnected from a superseded connection does not tear down the live one`() {
        val connection = connection()
        val stale = mockk<MediaController>(relaxed = true)
        val live = mockk<MediaController>(relaxed = true)

        connection.connect()
        futures[0].completeWith(stale)
        futures[0].dispatch()
        connection.disconnect()

        connection.connect()
        futures[1].completeWith(live)
        futures[1].dispatch()

        // The first connection's controller dies now, long after it stopped being the
        // one this class holds.
        listeners[0].onDisconnected(stale)

        assertThat(connection.controller).isSameInstanceAs(live)
        assertThat(connection.isConnected.value).isTrue()
        // A third attempt would mean the stale callback both dropped the live
        // connection and started rebuilding over the top of it.
        assertThat(futures).hasSize(2)
    }

    @Test
    fun `a connection that is lost is rebuilt once`() {
        val connection = connection()
        val controller = mockk<MediaController>(relaxed = true)

        connection.connect()
        futures[0].completeWith(controller)
        futures[0].dispatch()

        listeners[0].onDisconnected(controller)

        assertThat(futures).hasSize(2)
        assertThat(connection.isConnected.value).isFalse()
    }

    @Test
    fun `a deliberate disconnect is not rebuilt`() {
        val connection = connection()
        val controller = mockk<MediaController>(relaxed = true)
        // Releasing the controller is what dispatches onDisconnected, and it lands in
        // the middle of disconnect() — while the field the identity check reads still
        // holds this very future. Only the isConnected gate tells this apart from a
        // connection that was lost.
        every { controller.release() } answers { listeners[0].onDisconnected(controller) }

        connection.connect()
        futures[0].completeWith(controller)
        futures[0].dispatch()

        connection.disconnect()

        assertThat(futures).hasSize(1)
        assertThat(connection.isConnected.value).isFalse()
    }

    @Test
    fun `a failed attempt leaves nothing behind that would block the next connect`() {
        val connection = connection()
        val controller = mockk<MediaController>(relaxed = true)

        connection.connect()
        futures[0].failWith(IllegalStateException("service refused to bind"))
        futures[0].dispatch()

        connection.connect()
        futures[1].completeWith(controller)
        futures[1].dispatch()

        assertThat(connection.controller).isSameInstanceAs(controller)
        assertThat(connection.isConnected.value).isTrue()
    }
}
