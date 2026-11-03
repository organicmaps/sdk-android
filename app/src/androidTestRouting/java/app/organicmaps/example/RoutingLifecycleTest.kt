package app.organicmaps.example

import android.view.View
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.routing.ResultCodes
import app.organicmaps.sdk.routing.RoutingController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutingLifecycleTest {
    @Test
    fun missingMapErrorAndDownloadActionSurviveRecreation() {
        launchSample().use { scenario ->
            scenario.await { it.findViewById<View>(R.id.route_status) != null }
            scenario.onActivity {
                (it as RoutingActivity).onCommonBuildError(ResultCodes.NEED_MORE_MAPS, arrayOf("Andorra"))
            }
            scenario.recreate()
            scenario.await {
                it.findViewById<TextView>(R.id.route_status)?.text == it.getString(R.string.route_failed, 9)
            }
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.missing_maps).visibility)
                assertTrue(it.findViewById<Button>(R.id.download_missing_maps).isEnabled)
                assertTrue(it.findViewById<TextView>(R.id.missing_maps).text.contains("Andorra"))
            }
        }
    }

    @Test
    fun planningSurvivesBackgroundButFinishingClearsIt() {
        launchSample().use { scenario ->
            scenario.await { it.findViewById<View>(R.id.route_status) != null }
            scenario.onActivity { it.findViewById<Spinner>(R.id.route_profile).setSelection(1) }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val routingActivity = activity as RoutingActivity
                routingActivity.onPlacePageActivated(point("Origin", 42.50))
                routingActivity.click(R.string.use_origin)
                routingActivity.onPlacePageActivated(point("Destination", 42.51))
                routingActivity.click(R.string.use_destination)
                routingActivity.click(R.string.build_route)
            }
            scenario.await { RoutingController.get().isPlanning }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertTrue(RoutingController.get().isPlanning) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.await {
                it.findViewById<TextView>(R.id.route_endpoints)?.text?.contains("Origin") == true
            }
            scenario.onActivity {
                assertTrue(it.findViewById<TextView>(R.id.route_endpoints).text.contains("Destination"))
                assertEquals(1, it.findViewById<Spinner>(R.id.route_profile).selectedItemPosition)
                assertTrue(RoutingController.get().isPlanning)
            }
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(RoutingController.get().isPlanning)
        }
        launchSample().use { scenario ->
            scenario.onActivity { assertFalse(it.findViewById<Button>(R.id.start_navigation).isEnabled) }
        }
    }

    @Test
    fun identicalEndpointsFailBeforeStartingABuild() {
        launchSample().use { scenario ->
            scenario.await { it.findViewById<View>(R.id.route_status) != null }
            scenario.onActivity { activity ->
                val routingActivity = activity as RoutingActivity
                routingActivity.onPlacePageActivated(point("Same place", 42.50))
                routingActivity.click(R.string.use_origin)
                routingActivity.onPlacePageActivated(point("Same place", 42.50))
                routingActivity.click(R.string.use_destination)
                routingActivity.click(R.string.build_route)
                assertEquals(
                    activity.getString(R.string.same_endpoints),
                    activity.findViewById<TextView>(R.id.route_status).text,
                )
                assertFalse(RoutingController.get().isBuilding)
            }
        }
    }

    private fun point(title: String, latitude: Double) =
        MapObject.createMapObject(MapObject.POI, title, "", latitude, 1.52)
}
