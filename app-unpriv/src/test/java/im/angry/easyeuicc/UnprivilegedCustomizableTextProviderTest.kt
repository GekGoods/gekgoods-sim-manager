package im.angry.easyeuicc

import androidx.test.core.app.ApplicationProvider
import im.angry.openeuicc.di.UnprivilegedCustomizableTextProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnprivilegedCustomizableTextProviderTest {
    @Test
    fun officialWebsitePointsToGekGoodsSite() {
        val provider = UnprivilegedCustomizableTextProvider(
            ApplicationProvider.getApplicationContext()
        )

        assertEquals(
            android.net.Uri.parse("https://gekgoods.github.io"),
            provider.websiteUri
        )
    }
}
