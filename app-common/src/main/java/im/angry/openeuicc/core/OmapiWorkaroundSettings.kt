package im.angry.openeuicc.core

import android.content.Context
import android.os.Build

/** Synchronous policy reads are required before any OMAPI teardown starts. */
class OmapiWorkaroundSettings(context: Context) {
    private val preferences = context.getSharedPreferences("omapi_workaround_settings", Context.MODE_PRIVATE)

    var mode: String
        get() = preferences.getString("mode", "automatic")
            ?.takeIf { it in setOf("automatic", "always", "disabled") } ?: "automatic"
        set(value) {
            require(value in setOf("automatic", "always", "disabled"))
            check(preferences.edit().putString("mode", value).commit()) { "Could not save OMAPI mode" }
        }

    val isEnabled: Boolean
        get() = when (mode) {
            "always" -> true
            "disabled" -> false
            else -> Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        }
}
