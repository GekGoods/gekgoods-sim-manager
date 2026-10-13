package im.angry.openeuicc.ui

import im.angry.openeuicc.OpenEuiccApplication
import im.angry.openeuicc.di.DefaultAppContainer
import im.angry.openeuicc.di.UnprivilegedUiComponentFactory
import im.angry.openeuicc.util.UnprivilegedPreferenceRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class CompatibilityTestFragment : QuickCompatibilityFragment() {
    override fun formatDeviceInformation() = "Test device"
}

class CompatibilityTestApplication : OpenEuiccApplication() {
    override val appContainer by lazy {
        object : DefaultAppContainer(this) {
            override val uiComponentFactory = object : UnprivilegedUiComponentFactory() {
                override fun createQuickCompatibilityFragment() = CompatibilityTestFragment()
            }
            override val preferenceRepository = UnprivilegedPreferenceRepository(this@CompatibilityTestApplication)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = CompatibilityTestApplication::class)
class OmapiCompatibilityGuardTest {
    @Test
    fun `compatibility probe reports paused before connecting to OMAPI`() {
        ShadowBuild.setManufacturer("samsung")
        val app = RuntimeEnvironment.getApplication() as CompatibilityTestApplication
        app.getSharedPreferences("omapi_timeout_guard", 0).edit().clear().commit()
        app.getSharedPreferences("omapi_workaround_settings", 0).edit().clear().commit()
        app.appContainer.euiccChannelFactory.markOmapiReconnectTimedOut()
        val controller = Robolectric.buildActivity(QuickCompatibilityActivity::class.java).create()
        try {
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.fragments.single() as QuickCompatibilityFragment
            val method = QuickCompatibilityFragment::class.java.getDeclaredMethod(
                "getCompatibilityCheckResult", Continuation::class.java).apply { isAccessible = true }
            val result = method.invoke(fragment, object : Continuation<Any?> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Any?>) {}
            })
            assertNotSame("A blocked probe must return without waiting for an SE connection", COROUTINE_SUSPENDED, result)
            assertEquals("PAUSED", (result as QuickCompatibilityFragment.Companion.CompatibilityResult).compatibility.name)
        } finally {
            controller.destroy()
        }
    }
}
