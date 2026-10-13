package im.angry.openeuicc.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.se.omapi.SEService
import im.angry.openeuicc.common.R
import im.angry.openeuicc.di.DefaultAppContainer
import im.angry.openeuicc.testutil.MockEuiccChannel
import im.angry.openeuicc.util.FakeUiccCardInfoCompat
import im.angry.openeuicc.util.FakeUiccPortInfoCompat
import im.angry.openeuicc.util.UiccCardInfoCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowBuild
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OmapiTimeoutGuardTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("omapi_timeout_guard", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("omapi_workaround_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun guard(boot: Int = 42, enabled: Boolean = true) =
        OmapiTimeoutGuard(context, enabled) { boot }

    private class RecordingFactory(context: Context, private val guard: OmapiTimeoutGuard) :
        DefaultEuiccChannelFactory(context) {
        override val timeoutGuard: OmapiTimeoutGuard get() = guard
        var shutdownCalls = 0
        override fun shutdownOmapiService(service: SEService) { shutdownCalls += 1 }
    }

    private fun installFakeService(factory: RecordingFactory): SEService {
        val service = SEService(context, Executor { it.run() }, SEService.OnConnectedListener {})
        ReflectionHelpers.setField(factory, "seService", service)
        return service
    }

    @Test
    fun `healthy factory calls normal shutdown and clears its service`() {
        val factory = RecordingFactory(context, guard())
        installFakeService(factory)
        factory.cleanup()
        assertEquals(1, factory.shutdownCalls)
        assertNull(ReflectionHelpers.getField<SEService?>(factory, "seService"))
    }

    @Test
    fun `timeout factory skips shutdown even on repeated cleanup`() {
        val factory = RecordingFactory(context, guard())
        val service = installFakeService(factory)
        factory.markOmapiReconnectTimedOut()
        factory.cleanup()
        factory.cleanup()
        assertEquals(0, factory.shutdownCalls)
        assertSame(service, ReflectionHelpers.getField<SEService>(factory, "seService"))
    }

    @Test
    fun `new factory in the same boot cannot reopen OMAPI`() = runBlocking {
        guard().markReconnectTimedOut()
        val factory = RecordingFactory(context, guard())
        assertTrue(factory.isOmapiQuarantined)
        assertNull(factory.tryOpenEuiccChannel(
            FakeUiccPortInfoCompat(FakeUiccCardInfoCompat(0)), byteArrayOf(), EuiccChannel.SecureElementId.DEFAULT))
    }

    @Test
    fun `new process after reboot can use normal cleanup again`() {
        guard(42).markReconnectTimedOut()
        val factory = RecordingFactory(context, guard(43))
        assertFalse(factory.isOmapiQuarantined)
        installFakeService(factory)
        factory.cleanup()
        assertEquals(1, factory.shutdownCalls)
    }

    @Test
    fun `disabling protection cannot clear a previously unsafe boot`() {
        guard().markReconnectTimedOut()
        val factory = RecordingFactory(context, guard(enabled = false))
        factory.markOmapiReconnectTimedOut()
        assertTrue(factory.isOmapiQuarantined)
        installFakeService(factory)
        factory.cleanup()
        assertEquals(0, factory.shutdownCalls)
    }

    @Test
    fun `unknown boot identity stays fail closed across new instances`() {
        guard(-1).markReconnectTimedOut()
        assertTrue(guard(-1).isBlocked)
        assertTrue(guard(43).isBlocked)
    }

    @Test
    fun `unavailable boot identity cannot clear a known marker`() {
        guard(42).markReconnectTimedOut()
        assertTrue(OmapiTimeoutGuard(context, true) { throw SecurityException() }.isBlocked)
    }

    @Test
    fun `explicit recovery permits opening but never restores bulk shutdown`() {
        val guard = guard()
        val factory = RecordingFactory(context, guard)
        installFakeService(factory)
        factory.markOmapiReconnectTimedOut()
        assertTrue(factory.tryResumeOmapiAfterTimeout())
        assertFalse(factory.isOmapiQuarantined)
        factory.cleanup()
        assertEquals(0, factory.shutdownCalls)
        assertTrue("Process restart must require another explicit override", guard().isBlocked)
        factory.markOmapiReconnectTimedOut()
        assertTrue("Another timeout must revoke recovery permission", factory.isOmapiQuarantined)
    }

    @Test
    fun `always mode enables protection on a non Samsung device`() {
        ShadowBuild.setManufacturer("ExampleVendor")
        context.getSharedPreferences("omapi_workaround_settings", Context.MODE_PRIVATE)
            .edit().putString("mode", "always").commit()
        val factory = DefaultEuiccChannelFactory(context)
        factory.markOmapiReconnectTimedOut()
        assertTrue(factory.isOmapiQuarantined)
    }

    @Test
    fun `disabled mode on Samsung does not arm a healthy boot`() {
        ShadowBuild.setManufacturer("samsung")
        context.getSharedPreferences("omapi_workaround_settings", Context.MODE_PRIVATE)
            .edit().putString("mode", "disabled").commit()
        val factory = DefaultEuiccChannelFactory(context)
        factory.markOmapiReconnectTimedOut()
        assertFalse(factory.isOmapiQuarantined)
    }

    @Test
    fun `timeout overlapping a recovery request revokes permission`() {
        guard().markReconnectTimedOut()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val recoveringGuard = OmapiTimeoutGuard(context, true) {
            if (Thread.currentThread().name == "test-recovery") {
                entered.countDown()
                check(release.await(2, TimeUnit.SECONDS))
            }
            42
        }
        val worker = Thread({ recoveringGuard.tryResume() }, "test-recovery")
        val timer = Executors.newSingleThreadScheduledExecutor()
        try {
            worker.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            // Release an in-flight open-gate check while timeout revocation is racing it.
            timer.schedule({ release.countDown() }, 100, TimeUnit.MILLISECONDS)
            recoveringGuard.markReconnectTimedOut()
            worker.join(2000)
            assertFalse(worker.isAlive)
            assertTrue("The overlapping timeout must win over recovery", recoveringGuard.isBlocked)
        } finally {
            release.countDown()
            worker.join(2000)
            timer.shutdownNow()
        }
    }

    @Test
    fun `OMAPI protection does not swallow a TelephonyManager close failure`() = runBlocking {
        ShadowBuild.setManufacturer("samsung")
        val factory = RecordingFactory(context, guard())
        val manager = manager(factory)
        val channel = object : EuiccChannel by MockEuiccChannel(0, 0) {
            override val type = "TelephonyManager"
            override fun close() { throw IOException("test close failure") }
        }
        ReflectionHelpers.setField(manager, "channelCache", mutableListOf(channel))
        try {
            manager.waitForReconnect(0, 0, 30)
            fail("Expected original close failure")
        } catch (e: Exception) {
            assertTrue("OMAPI setting must not change the other backend's close behavior", e is IOException)
            assertFalse(factory.isOmapiQuarantined)
        }
    }

    private fun manager(factory: RecordingFactory) = object : DefaultEuiccChannelManager(
        object : DefaultAppContainer(context) { override val euiccChannelFactory = factory }, context
    ) {
        override val uiccCards: Collection<UiccCardInfoCompat> get() = emptyList()
        override suspend fun tryOpenUsbEuiccChannel(): Pair<UsbDevice?, Boolean> = null to false
    }

    @Test
    fun `OMAPI reconnect latches before timeout is returned to the caller`() = runBlocking {
        val factory = RecordingFactory(context, guard())
        val manager = manager(factory)
        val channel = object : EuiccChannel by MockEuiccChannel(0, 0) {
            override val type = context.getString(R.string.channel_type_omapi)
        }
        ReflectionHelpers.setField(manager, "channelCache", mutableListOf(channel))
        try {
            manager.waitForReconnect(0, 0, 30)
            fail("Expected reconnect timeout")
        } catch (_: TimeoutCancellationException) {
            assertTrue(factory.isOmapiQuarantined)
        }
    }

    @Test
    fun `USB reconnect timeout does not quarantine OMAPI`() = runBlocking {
        val factory = RecordingFactory(context, guard())
        val manager = manager(factory)
        ReflectionHelpers.setField(manager, "usbChannels", mutableListOf(
            MockEuiccChannel(EuiccChannelManager.USB_CHANNEL_ID, 0)))
        try {
            manager.waitForReconnect(EuiccChannelManager.USB_CHANNEL_ID, 0, 30)
            fail("Expected reconnect timeout")
        } catch (_: TimeoutCancellationException) {
            assertFalse(factory.isOmapiQuarantined)
        }
    }

    @Test
    fun `quarantined invalidation abandons OMAPI but still closes USB`() {
        val factory = RecordingFactory(context, guard())
        val manager = manager(factory)
        var omapiCloses = 0
        var usbCloses = 0
        val omapi = object : EuiccChannel by MockEuiccChannel(0, 0) {
            override val type = context.getString(R.string.channel_type_omapi)
            override fun close() { omapiCloses += 1 }
        }
        val usb = object : EuiccChannel by MockEuiccChannel(EuiccChannelManager.USB_CHANNEL_ID, 0) {
            override fun close() { usbCloses += 1 }
        }
        ReflectionHelpers.setField(manager, "channelCache", mutableListOf(omapi))
        ReflectionHelpers.setField(manager, "usbChannels", mutableListOf(usb))
        factory.markOmapiReconnectTimedOut()
        manager.invalidate()
        assertEquals(0, omapiCloses)
        assertEquals(1, usbCloses)
        assertTrue(ReflectionHelpers.getField<List<EuiccChannel>>(manager, "channelCache").isEmpty())
    }
}
