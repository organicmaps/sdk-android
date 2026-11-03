package app.organicmaps.example

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.search.MapSearchListener
import app.organicmaps.sdk.search.SearchEngine
import app.organicmaps.sdk.search.SearchListener
import app.organicmaps.sdk.search.SearchResult

/** Region-name search, explicit downloads, and offline POI search using request-specific snapshots. */
class OfflineSearchActivity :
    MainActivity(),
    MapSearchListener,
    SearchListener,
    MapManager.StorageCallback {
    private lateinit var regionStatus: TextView
    private lateinit var searchStatus: TextView
    private lateinit var resultsView: ListView
    private lateinit var resultsAdapter: ArrayAdapter<String>
    private var selectedRegion: String? = null
    private var regionRequest = -1L
    private var placeRequest = -1L
    private var regions = emptyList<MapSearchListener.Result>()
    private var places = emptyList<Pair<Int, SearchResult>>()
    private var storageSlot = -1
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        selectedRegion = savedInstanceState?.getString("region")
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("region", selectedRegion)
        super.onSaveInstanceState(outState)
    }

    override fun onCoreInitialized() {
        super.onCoreInitialized()
        val panel = samplePanel()
        val regionQuery = EditText(this).apply {
            id = R.id.region_query
            setHint(R.string.region_query)
        }
        panel.addView(regionQuery)
        panel.button(R.string.find_regions) {
            placeRequest = -1
            regions = emptyList()
            resultsAdapter.clear()
            searchStatus.setText(R.string.searching)
            regionRequest = System.nanoTime()
            SearchEngine.searchMaps(this, regionQuery.text.toString(), regionRequest)
        }
        val downloads = LinearLayout(this)
        panel.addView(downloads)
        downloads.button(R.string.download_region) { selectedRegion?.let(::download) }
        downloads.button(R.string.cancel_download) { selectedRegion?.let(MapManager::nativeCancel) }
        downloads.button(R.string.retry_download) { selectedRegion?.let(::download) }
        regionStatus = TextView(this).apply {
            id = R.id.region_status
            setText(R.string.choose_region)
        }
        panel.addView(regionStatus)
        val placeQuery = EditText(this).apply {
            id = R.id.place_query
            setHint(R.string.place_query)
        }
        panel.addView(placeQuery)
        panel.button(R.string.find_places) {
            regionRequest = -1
            placeRequest = System.nanoTime()
            places = emptyList()
            resultsAdapter.clear()
            searchStatus.setText(R.string.searching)
            if (!SearchEngine.INSTANCE.search(this, placeQuery.text.toString(), false, placeRequest, false, 0.0, 0.0)) {
                searchStatus.setText(R.string.search_not_started)
            }
        }
        searchStatus = TextView(this).apply {
            id = R.id.search_status
        }
        panel.addView(searchStatus)
        resultsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        resultsView = ListView(this).apply {
            id = R.id.search_results
            isNestedScrollingEnabled = true
            adapter = resultsAdapter
        }
        panel.addView(
            resultsView,
            LinearLayout.LayoutParams(MATCH_PARENT, (160 * resources.displayMetrics.density).toInt()),
        )
        resultsView.setOnItemClickListener { _, _, position, _ ->
            if (regionRequest >= 0) {
                selectedRegion = regions[position].countryId()
                refreshRegion()
            } else {
                SearchEngine.INSTANCE.showResult(places[position].first)
            }
        }
        initialized = true
        subscribe()
    }

    private fun download(id: String) {
        if (!downloadRegion(id)) regionStatus.setText(R.string.storage_full)
    }

    private fun subscribe() {
        if (!initialized || storageSlot >= 0) return
        SearchEngine.INSTANCE.addMapListener(this)
        SearchEngine.INSTANCE.addListener(this)
        storageSlot = MapManager.nativeSubscribe(this)
        refreshRegion()
    }

    override fun onStart() {
        super.onStart()
        subscribe()
    }

    override fun onStop() {
        if (storageSlot >= 0) {
            // Invalidate before cancellation: delivery already queued on the main thread may still arrive.
            regionRequest = -1
            placeRequest = -1
            resultsAdapter.clear()
            SearchEngine.INSTANCE.removeMapListener(this)
            SearchEngine.INSTANCE.removeListener(this)
            SearchEngine.INSTANCE.cancel()
            MapManager.nativeUnsubscribe(storageSlot)
            storageSlot = -1
        }
        super.onStop()
    }

    override fun onMapSearchResults(results: Array<MapSearchListener.Result>, timestamp: Long, isLast: Boolean) {
        if (timestamp != regionRequest) return
        regions = results.toList()
        showResults(regions.map { it.matchedString() })
        if (isLast) {
            searchStatus.text = resources.getQuantityString(R.plurals.regions_complete, regions.size, regions.size)
        }
    }

    override fun onResultsUpdate(results: Array<SearchResult>, timestamp: Long) {
        if (timestamp != placeRequest) return
        // Preserve native indexes when suggestions are filtered out of the display list.
        places = results.mapIndexedNotNull { index, result ->
            if (result.type == SearchResult.TYPE_RESULT) index to result else null
        }
        showResults(places.map { it.second.getTitle(this) })
    }

    private fun showResults(labels: List<String>) {
        // One notification keeps ListView's scroll state across complete snapshots of the same request.
        resultsAdapter.setNotifyOnChange(false)
        resultsAdapter.clear()
        resultsAdapter.addAll(labels)
        resultsAdapter.notifyDataSetChanged()
    }

    override fun onResultsEnd(timestamp: Long) {
        if (timestamp == placeRequest) {
            searchStatus.text = resources.getQuantityString(R.plurals.search_complete, places.size, places.size)
        }
    }

    override fun onStatusChanged(data: List<MapManager.StorageCallbackData>) {
        if (data.any { it.countryId == selectedRegion }) refreshRegion()
    }

    override fun onProgress(countryId: String, localSize: Long, remoteSize: Long) {
        if (countryId == selectedRegion && remoteSize > 0) {
            regionStatus.text = getString(R.string.download_progress, localSize * 100 / remoteSize)
        }
    }

    private fun refreshRegion() {
        val id = selectedRegion ?: return
        val region = CountryItem.fill(id)
        val state = when (region.status) {
            CountryItem.STATUS_DONE, CountryItem.STATUS_UPDATABLE -> getString(R.string.map_ready)

            CountryItem.STATUS_FAILED -> getString(R.string.download_failed, region.errorCode)

            CountryItem.STATUS_ENQUEUED, CountryItem.STATUS_PROGRESS, CountryItem.STATUS_APPLYING -> getString(
                R.string.downloading,
            )

            else -> getString(R.string.map_available)
        }
        regionStatus.text = getString(R.string.region_status, region.name, state)
    }
}
