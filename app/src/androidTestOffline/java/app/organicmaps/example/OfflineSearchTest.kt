package app.organicmaps.example

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.search.MapSearchListener
import app.organicmaps.sdk.search.SearchEngine
import app.organicmaps.sdk.search.SearchListener
import app.organicmaps.sdk.search.SearchResult
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSearchTest :
    MapSearchListener,
    SearchListener {
    private var regionResults = emptyArray<MapSearchListener.Result>()
    private var regionTimestamp = -1L
    private var regionsComplete = false
    private var placeResults = emptyArray<SearchResult>()
    private var placeTimestamp = -1L
    private var placesComplete = false

    @After
    fun removeListeners() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            SearchEngine.INSTANCE.removeMapListener(this)
            SearchEngine.INSTANCE.removeListener(this)
        }
    }

    @Test
    fun resultListScrollsInsideTheSamplePanel() {
        launchOffline().use { scenario ->
            findRegions(scenario, "a")
            scenario.await { regionsComplete && it.findViewById<ListView>(R.id.search_results).count > 5 }
            val bounds = showResults(scenario)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val down = SystemClock.uptimeMillis()
            val x = bounds.centerX().toFloat()
            val start = bounds.bottom - 20f
            val end = bounds.top + 20f
            fun pointer(action: Int, y: Float) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
            pointer(MotionEvent.ACTION_DOWN, start)
            for (step in 1..15) {
                SystemClock.sleep(20)
                pointer(MotionEvent.ACTION_MOVE, start + (end - start) * step / 15)
            }
            pointer(MotionEvent.ACTION_UP, end)
            scenario.await { it.findViewById<ListView>(R.id.search_results).firstVisiblePosition > 0 }
        }
    }

    @Test
    fun incrementalSnapshotsPreserveScrolling() {
        launchOffline().use { scenario ->
            // Hold native delivery so two snapshots can be presented around a user scroll deterministically.
            scenario.onActivity { SearchEngine.INSTANCE.removeMapListener(it as OfflineSearchActivity) }
            findRegions(scenario, "a")
            scenario.await { regionsComplete && regionResults.size > 12 }
            scenario.onActivity {
                (it as OfflineSearchActivity).onMapSearchResults(
                    regionResults.take(12).toTypedArray(),
                    regionTimestamp,
                    false,
                )
            }
            showResults(scenario)
            scenario.onActivity { it.findViewById<ListView>(R.id.search_results).setSelection(8) }
            scenario.await { it.findViewById<ListView>(R.id.search_results).firstVisiblePosition == 8 }
            scenario.onActivity {
                (it as OfflineSearchActivity).onMapSearchResults(regionResults, regionTimestamp, true)
            }
            scenario.await { !it.findViewById<ListView>(R.id.search_results).isLayoutRequested }
            scenario.onActivity {
                val results = it.findViewById<ListView>(R.id.search_results)
                assertEquals(regionResults.size, results.count)
                assertEquals(8, results.firstVisiblePosition)
            }
        }
    }

    @Test
    fun stoppedSearchRejectsLateRegionAndPlaceCallbacks() {
        launchOffline().use { scenario ->
            findRegions(scenario, "a")
            scenario.await { regionsComplete && regionResults.isNotEmpty() }
            val stoppedRegions = regionResults.copyOf()
            val stoppedRegionTimestamp = regionTimestamp
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                val activity = it as OfflineSearchActivity
                activity.onMapSearchResults(stoppedRegions, stoppedRegionTimestamp, true)
                assertEquals(0, activity.findViewById<ListView>(R.id.search_results).count)
            }

            findPlaces(scenario, "par")
            scenario.await { placesComplete && placeResults.any { it.type == SearchResult.TYPE_RESULT } }
            val stoppedPlaces = placeResults.copyOf()
            val stoppedPlaceTimestamp = placeTimestamp
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                val activity = it as OfflineSearchActivity
                val feedback = activity.findViewById<TextView>(R.id.search_status).text.toString()
                activity.onResultsUpdate(stoppedPlaces, stoppedPlaceTimestamp)
                activity.onResultsEnd(stoppedPlaceTimestamp)
                assertEquals(0, activity.findViewById<ListView>(R.id.search_results).count)
                assertEquals(feedback, activity.findViewById<TextView>(R.id.search_status).text.toString())
            }
        }
    }

    @Test
    fun filteredSuggestionsKeepNativeSelectionIndexes() {
        launchOffline().use { scenario ->
            findPlaces(scenario, "par")
            scenario.await { placesComplete }
            val nativeIndex = placeResults.indexOfFirst { it.type == SearchResult.TYPE_RESULT }
            assertTrue("The query must exercise a suggestion before a real result", nativeIndex > 0)
            val result = placeResults[nativeIndex]
            val coordinates = String.format(Locale.US, "%.5f, %.5f", result.lat, result.lon)
            scenario.onActivity {
                val results = it.findViewById<ListView>(R.id.search_results)
                assertEquals(placeResults.count { it.type == SearchResult.TYPE_RESULT }, results.count)
                results.performItemClick(null, 0, 0)
            }
            scenario.await {
                it.findViewById<View>(R.id.root).descendants().filterIsInstance<TextView>()
                    .any { view -> view.text.contains(coordinates) }
            }
        }
    }

    @Test
    fun selectedDownloadCanBeCancelled() {
        launchOffline().use { scenario ->
            findRegions(scenario, "Andorra")
            scenario.await { regionsComplete && regionResults.isNotEmpty() }
            scenario.onActivity {
                it.findViewById<ListView>(R.id.search_results).performItemClick(null, 0, 0)
                // One UI turn prevents completion from racing the cancellation check.
                it.click(R.string.download_region)
                try {
                    assertTrue(MapManager.isDownloading())
                    it.click(R.string.cancel_download)
                    assertFalse(MapManager.isDownloading())
                } finally {
                    MapManager.cancel("Andorra")
                }
            }
        }
    }

    @Test
    fun downloadEventsPreserveSearchFeedback() {
        launchOffline().use { scenario ->
            findRegions(scenario, "Andorra")
            scenario.await { regionsComplete && regionResults.isNotEmpty() }
            scenario.onActivity { it.findViewById<ListView>(R.id.search_results).performItemClick(null, 0, 0) }
            findPlaces(scenario, "par")
            scenario.await { placesComplete }
            scenario.onActivity {
                val activity = it as OfflineSearchActivity
                val search = activity.findViewById<TextView>(R.id.search_status)
                val region = activity.findViewById<TextView>(R.id.region_status)
                val searchFeedback = search.text.toString()
                // Different map and terrain fractions distinguish combined progress from either stream alone.
                activity.onProgress("Andorra", MapManager.Progress(25, 100, 75, 100))
                assertEquals(activity.getString(R.string.download_progress, 50), region.text.toString())
                activity.onProgress("Andorra", MapManager.Progress(0, 0, 0, 0))
                assertEquals(activity.getString(R.string.download_progress, 50), region.text.toString())
                activity.onStatusChanged(
                    listOf(MapManager.StorageCallbackData("Germany", CountryItem.STATUS_DONE, 0, false)),
                )
                assertEquals(activity.getString(R.string.download_progress, 50), region.text.toString())
                activity.onStatusChanged(
                    listOf(MapManager.StorageCallbackData("Andorra", CountryItem.STATUS_DONE, 0, true)),
                )
                assertTrue(region.text.contains("Andorra"))
                assertEquals(searchFeedback, search.text.toString())
            }
        }
    }

    private fun launchOffline(): ActivityScenario<MainActivity> = launchSample().also { scenario ->
        scenario.onActivity {
            SearchEngine.INSTANCE.addMapListener(this)
            SearchEngine.INSTANCE.addListener(this)
        }
    }

    private fun findRegions(scenario: ActivityScenario<MainActivity>, query: String) {
        scenario.onActivity {
            regionsComplete = false
            it.findViewById<EditText>(R.id.region_query).setText(query)
            it.click(R.string.find_regions)
        }
    }

    private fun findPlaces(scenario: ActivityScenario<MainActivity>, query: String) {
        scenario.onActivity {
            placesComplete = false
            it.findViewById<EditText>(R.id.place_query).setText(query)
            it.click(R.string.find_places)
        }
    }

    private fun showResults(scenario: ActivityScenario<MainActivity>): Rect {
        scenario.onActivity {
            it.findViewById<ScrollView>(R.id.sample_scroll).apply {
                isSmoothScrollingEnabled = false
                fullScroll(View.FOCUS_DOWN)
            }
        }
        val bounds = Rect()
        scenario.await {
            val results = it.findViewById<ListView>(R.id.search_results)
            !results.isLayoutRequested && results.getGlobalVisibleRect(bounds) && bounds.height() > 80
        }
        return bounds
    }

    override fun onMapSearchResults(results: Array<MapSearchListener.Result>, timestamp: Long, isLast: Boolean) {
        regionResults = results
        regionTimestamp = timestamp
        regionsComplete = isLast
    }

    override fun onResultsUpdate(results: Array<SearchResult>, timestamp: Long) {
        placeResults = results
        placeTimestamp = timestamp
    }

    override fun onResultsEnd(timestamp: Long) {
        if (timestamp == placeTimestamp) placesComplete = true
    }
}
