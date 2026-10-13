package im.angry.openeuicc.ui

import androidx.preference.ListPreference
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
        timeout: Boolean = false, check: (ListPreference) -> Unit) {
        ShadowBuild.setManufacturer(manufacturer)
        val app = RuntimeEnvironment.getApplication() as OpenEuiccApplication
        app.getSharedPreferences("omapi_timeout_guard", 0).edit().clear().commit()
        app.getSharedPreferences("omapi_workaround_settings", 0).edit().clear().commit()
        OmapiWorkaroundSettings(app).mode = mode
        if (timeout) app.appContainer.euiccChannelFactory.markOmapiReconnectTimedOut()
        if (timeout) OmapiWorkaroundSettings(app).mode = "disabled"
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.fragments.single() as SettingsFragment
            check(fragment.findPreference<ListPreference>("pref_omapi_protection_mode")!!)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `Samsung automatic status is prominent in title and dropdown`() {
        checkDisplay("samsung") {
            assertTrue(it.title.toString().endsWith("— ON"))
            assertTrue(it.entries[0].toString().contains("ON on this device"))
            assertTrue(it.summary.toString().startsWith("Automatic · Samsung detected"))
        }
    }

    @Test
    fun `non Samsung automatic status clearly says OFF`() {
        checkDisplay("ExampleVendor") {
            assertTrue(it.title.toString().endsWith("— OFF"))
            assertTrue(it.entries[0].toString().contains("OFF on this device"))
        }
    }

    @Test
    fun `manual enabled does not mislabel automatic on a non Samsung device`() {
        checkDisplay("ExampleVendor", "always") {
            assertTrue(it.title.toString().endsWith("— ON"))
            assertTrue(it.entries[0].toString().contains("OFF on this device"))
            assertTrue(it.summary.toString().contains("manual override"))
        }
    }

    @Test
    fun `disabled policy cannot hide active timeout cleanup protection`() {
        checkDisplay("samsung", timeout = true) {
            assertTrue(it.title.toString().contains("ON (timeout latch)"))
            assertTrue(it.summary.toString().contains("Disabled"))
            assertTrue(it.summary.toString().contains("ACTIVE until reboot"))
        }
    }
}
