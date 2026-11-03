package app.organicmaps.example

import android.app.Activity
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.LocationManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.organicmaps.sdk.Map as OMMap
import app.organicmaps.sdk.location.LocationState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class LocationResolutionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(intent.getIntExtra("result", RESULT_CANCELED))
        finish()
    }
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 23) // Per-app mock-location selection requires Android M.
class LocationResolutionTest {
    private lateinit var locationManager: LocationManager
    private var mockLocationMode = "default"
    private var testGpsInstalled = false

    @Before
    @Suppress("DEPRECATION") // The ProviderProperties overload requires API 31.
    fun installTestGps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow("android:mock_location", Process.myUid(), context.packageName)
        mockLocationMode = listOf("allow", "ignore", "deny", "default", "foreground")[mode]
        setMockLocationMode("allow")
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Resolution tests need polling state, without depending on physical fixes or the emulator's GNSS HAL.
        locationManager.addTestProvider(
            LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
        )
        testGpsInstalled = true
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    @After
    fun removeTestGps() {
        if (testGpsInstalled) locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        setMockLocationMode(mockLocationMode)
    }

    private fun setMockLocationMode(mode: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = instrumentation.uiAutomation.executeShellCommand(
            "appops set ${instrumentation.targetContext.packageName} android:mock_location $mode",
        )
        ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
    }

    @Test
    fun acceptedResolutionRestartsLocation() = resolveLocation(Activity.RESULT_OK)

    @Test
    fun cancelledResolutionKeepsLocationStopped() = resolveLocation(Activity.RESULT_CANCELED)

    private fun resolveLocation(result: Int) {
        launchSample().use { scenario ->
            scenario.await { OMMap.isEngineCreated() }
            scenario.onActivity { activity ->
                val helper = (activity.application as ExampleApplication).organicMaps.locationHelper
                helper.start()
                val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
                val intent = Intent().setClassName(testPackage, LocationResolutionActivity::class.java.name)
                    .putExtra("result", result)
                val resolution = PendingIntent.getActivity(activity, result, intent, PendingIntent.FLAG_IMMUTABLE)
                helper.onLocationResolutionRequired(resolution)
                assertFalse(helper.isActive)
            }
            if (result == Activity.RESULT_OK) {
                scenario.await { (it.application as ExampleApplication).organicMaps.locationHelper.isActive }
            } else {
                scenario.await { LocationState.getMode() == LocationState.NOT_FOLLOW_NO_POSITION }
                scenario.onActivity {
                    assertFalse((it.application as ExampleApplication).organicMaps.locationHelper.isActive)
                }
            }
        }
    }
}
