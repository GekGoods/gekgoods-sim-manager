package im.angry.openeuicc.core

import im.angry.openeuicc.di.DefaultAppContainer
import im.angry.openeuicc.testutil.MockEuiccChannel
import im.angry.openeuicc.util.UiccCardInfoCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultEuiccChannelManagerTest {
    @Test
    fun `reconnect removes stale channel when closing it fails`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val manager = object : DefaultEuiccChannelManager(DefaultAppContainer(context), context) {
            override val uiccCards: Collection<UiccCardInfoCompat> = emptyList()
        }
        val channel = MockEuiccChannel(slotId = 1, portId = 2).apply {
            closeFailure = IllegalStateException("OMAPI channel already closed")
        }
        cachedChannels(manager).add(channel)

        try {
            manager.waitForReconnect(physicalSlotId = 1, portId = 2, timeoutMillis = 100)
            fail("waitForReconnect should time out without a replacement channel")
        } catch (_: TimeoutCancellationException) {
            // The failing close must not abort reconnect cleanup.
        }

        assertEquals(1, channel.closeCalls)
        assertEquals(0, cachedChannels(manager).size)
    }

    @Suppress("UNCHECKED_CAST")
    private fun cachedChannels(manager: DefaultEuiccChannelManager): MutableList<EuiccChannel> =
        DefaultEuiccChannelManager::class.java.getDeclaredField("channelCache").let { field ->
            field.isAccessible = true
            field.get(manager) as MutableList<EuiccChannel>
        }
}
