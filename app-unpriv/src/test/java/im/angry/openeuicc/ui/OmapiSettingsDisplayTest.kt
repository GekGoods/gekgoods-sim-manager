package im.angry.openeuicc.ui

import androidx.preference.ListPreference
import androidx.preference.Preference
import im.angry.openeuicc.OpenEuiccApplication
import im.angry.openeuicc.core.OmapiWorkaroundSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = OpenEuiccApplication::class)
class OmapiSettingsDisplayTest {
    private fun checkDisplay(manufacturer: String, mode: String = "automatic",
        timeout: Boolean = false, disabledAfterTimeout: Boolean = false,
        recover: Boolean = false, check: (ListPreference, Preference?) -> Unit) {
        ShadowBuild.setManufacturer(manufacturer)
        val app = RuntimeEnvironment.getApplication() as OpenEuiccApplication
        app.getSharedPreferences("omapi_timeout_guard", 0).edit().clear().commit()
        app.getSharedPreferences("omapi_workaround_settings", 0).edit().clear().commit()
        OmapiWorkaroundSettings(app).mode = mode
        if (timeout) app.appContainer.euiccChannelFactory.markOmapiReconnectTimedOut()
        if (disabledAfterTimeout) OmapiWorkaroundSettings(app).mode = "disabled"
        if (recover) assertTrue(app.appContainer.euiccChannelFactory.tryResumeOmapiAfterTimeout())
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.fragments.single() as SettingsFragment
            check(fragment.findPreference<ListPreference>("pref_omapi_protection_mode")!!,
                fragment.findPreference<Preference>("pref_omapi_connection_state"))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `Samsung automatic status is prominent in title and dropdown`() {
        checkDisplay("samsung") { mode, state ->
            assertTrue(mode.title.toString().contains("Samsung eSIM connection workaround"))
            assertTrue(mode.title.toString().endsWith("— enabled"))
            assertTrue(mode.entries[0].toString().contains("enabled on this Samsung device"))
            assertTrue(mode.summary.toString().contains("high CPU"))
            assertEquals("Connection status — ready", state?.title.toString())
        }
    }

    @Test
    fun `non Samsung automatic status clearly says disabled`() {
        checkDisplay("ExampleVendor") { mode, state ->
            assertTrue(mode.title.toString().endsWith("— disabled"))
            assertTrue(mode.entries[0].toString().contains("not enabled (non-Samsung)"))
            assertTrue(mode.summary.toString().contains("Enable manually only for troubleshooting"))
            assertTrue(state?.summary.toString().contains("normal OMAPI behavior"))
        }
    }

    @Test
    fun `manual enabled does not mislabel automatic on a non Samsung device`() {
        checkDisplay("ExampleVendor", "always") { mode, _ ->
            assertTrue(mode.title.toString().endsWith("— enabled"))
            assertTrue(mode.entries[0].toString().contains("not enabled (non-Samsung)"))
            assertTrue(mode.summary.toString().contains("manual override"))
        }
    }

    @Test
    fun `disabled policy cannot hide active timeout cleanup protection`() {
        checkDisplay("samsung", timeout = true, disabledAfterTimeout = true) { mode, state ->
            assertTrue(mode.title.toString().endsWith("— disabled"))
            assertEquals("Connection status — paused after timeout", state?.title.toString())
            assertTrue(state?.summary.toString().contains("does not change your saved workaround setting"))
            assertTrue(state?.summary.toString().contains("cleanup remains blocked"))
        }
    }

    @Test
    fun `recovery status separates new connections from blocked cleanup`() {
        checkDisplay("samsung", timeout = true, recover = true) { mode, state ->
            assertTrue(mode.title.toString().endsWith("— enabled"))
            assertEquals("Connection status — experimental recovery active", state?.title.toString())
            assertTrue(state?.summary.toString().contains("New connections are allowed"))
            assertTrue(state?.summary.toString().contains("cleanup remains blocked"))
            assertTrue(state?.summary.toString().contains("does not change your saved workaround setting"))
        }
    }
}
