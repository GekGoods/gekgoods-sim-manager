package im.angry.openeuicc.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SeDiagnosticsTest {
    private fun messages() = ShadowLog.getLogsForTag(SeDiagnostics.TAG).map { it.msg }

    @Test
    fun `trace returns the original value and marks normal return`() {
        ShadowLog.clear()
        val result = Any()
        assertSame(result, SeDiagnostics.trace(this, "test.close") { result })
        assertEquals(2, messages().size)
        assertTrue(messages()[0].contains("event=test.close.enter"))
        assertTrue(messages()[1].contains("event=test.close.exit"))
        assertTrue(messages()[1].contains("returned=true"))
    }

    @Test
    fun `trace rethrows the original exception without logging its message`() {
        ShadowLog.clear()
        val failure = IllegalArgumentException("sensitive-profile-identifier")
        try {
            SeDiagnostics.trace<Unit>(this, "test.close") { throw failure }
            throw AssertionError("Expected original exception")
        } catch (caught: IllegalArgumentException) {
            assertSame(failure, caught)
        }
        assertEquals(3, messages().size)
        assertTrue(messages()[1].contains("type=IllegalArgumentException"))
        assertTrue(messages()[2].contains("returned=false"))
        assertFalse(messages().any { it.contains(failure.message!!) })
    }

    @Test
    fun `suspending trace preserves coroutine cancellation`() = runBlocking {
        ShadowLog.clear()
        val cancellation = CancellationException("sensitive-profile-identifier")
        try {
            SeDiagnostics.traceSuspending<Unit>(this, "test.reconnect") { throw cancellation }
            throw AssertionError("Expected cancellation")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        assertTrue(messages().last().contains("returned=false"))
        assertFalse(messages().any { it.contains(cancellation.message!!) })
    }
}
