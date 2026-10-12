package im.angry.openeuicc.util

import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/** Diagnostic-only spans: never query OMAPI state or log profile/APDU contents. */
object SeDiagnostics {
    const val TAG = "SE_DIAG"
    private val sequence = AtomicLong()

    fun event(owner: Any, name: String, details: String = "") {
        Log.i(TAG, "t=${SystemClock.elapsedRealtime()} tid=${Process.myTid()} " +
            "obj=${System.identityHashCode(owner).toString(16)} event=$name $details")
    }

    fun <T> trace(owner: Any, name: String, details: String = "", block: () -> T): T {
        val span = sequence.incrementAndGet()
        event(owner, "$name.enter", "span=$span $details")
        var returned = false
        try {
            val result = block()
            returned = true
            return result
        } catch (t: Throwable) {
            event(owner, "$name.throw", "span=$span type=${t.javaClass.simpleName}")
            throw t
        } finally {
            // "returned" does not imply hardware cleanup succeeded: public OMAPI may swallow errors.
            event(owner, "$name.exit", "span=$span returned=$returned")
        }
    }

    suspend fun <T> traceSuspending(
        owner: Any, name: String, details: String = "", block: suspend () -> T
    ): T {
        val span = sequence.incrementAndGet()
        event(owner, "$name.enter", "span=$span $details")
        var returned = false
        try {
            val result = block()
            returned = true
            return result
        } catch (t: Throwable) {
            event(owner, "$name.throw", "span=$span type=${t.javaClass.simpleName}")
            throw t
        } finally {
            event(owner, "$name.exit", "span=$span returned=$returned")
        }
    }
}
