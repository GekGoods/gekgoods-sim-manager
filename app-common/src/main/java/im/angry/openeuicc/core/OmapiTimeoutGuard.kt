package im.angry.openeuicc.core

import android.content.Context
import android.provider.Settings
import im.angry.openeuicc.util.SeDiagnostics

/** Reboot-scoped quarantine, not an attempt to repair Samsung's service-side channel lists. */
class OmapiTimeoutGuard(
    context: Context,
    private val enabled: Boolean,
    private val bootCount: () -> Int = {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }
) {
    private val preferences = context.getSharedPreferences("omapi_timeout_guard", Context.MODE_PRIVATE)
    @Volatile private var blockedInThisProcess = false

    val isBlocked: Boolean
        get() {
            if (!enabled) return false
            if (blockedInThisProcess) return true
            val markedBoot = preferences.getInt("unsafe_boot", Int.MIN_VALUE)
            if (markedBoot == Int.MIN_VALUE) return false
            val currentBoot = currentBootCount()
            // Unknown boot identity must not accidentally re-enable a poisoned connection.
            return markedBoot < 0 || currentBoot < 0 || markedBoot == currentBoot
        }

    fun markReconnectTimedOut() {
        if (!enabled) return
        blockedInThisProcess = true
        val boot = currentBootCount()
        val persisted = try {
            preferences.edit().putInt("unsafe_boot", boot).commit()
        } catch (_: Exception) {
            false
        }
        SeDiagnostics.event(this, "omapi.quarantine.marked", "persisted=$persisted bootKnown=${boot >= 0}")
    }

    private fun currentBootCount(): Int = try {
        bootCount().takeIf { it >= 0 } ?: -1
    } catch (_: Exception) {
        -1
    }
}
