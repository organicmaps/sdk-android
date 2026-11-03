package app.organicmaps.example

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.organicmaps.sdk.Map as OMMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapLifecycleTest {
    @Test
    fun compassSubscriptionFollowsActivityVisibilityAndRecreation() {
        launchSample().use { scenario ->
            scenario.await { OMMap.isEngineCreated() }
            scenario.await { activity ->
                val button = activity.findViewById<View>(R.id.my_position)
                val insets = ViewCompat.getRootWindowInsets(button)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: return@await false
                val position = IntArray(2)
                button.getLocationOnScreen(position)
                button.height > 0 && position[1] + button.height <= activity.window.decorView.height - insets.bottom
            }
            scenario.onActivity {
                val helper = (it.application as ExampleApplication).organicMaps.sensorHelper
                val listeners = helper.javaClass.getDeclaredField("mListeners").apply { isAccessible = true }
                assertEquals(setOf(it), listeners.get(helper))
                it.onCompassUpdated(0.5)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                val helper = (it.application as ExampleApplication).organicMaps.sensorHelper
                val listeners = helper.javaClass.getDeclaredField("mListeners").apply { isAccessible = true }
                assertTrue((listeners.get(helper) as Set<*>).isEmpty())
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.await { OMMap.isEngineCreated() }
            scenario.onActivity {
                val helper = (it.application as ExampleApplication).organicMaps.sensorHelper
                val listeners = helper.javaClass.getDeclaredField("mListeners").apply { isAccessible = true }
                assertEquals(setOf(it), listeners.get(helper))
            }
        }
    }
}
