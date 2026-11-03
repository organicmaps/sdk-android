package app.organicmaps.example

import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.core.os.BundleCompat
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.location.LocationUtils
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.widget.placepage.PlacePageData

/** Route plans survive backgrounding and recreation; finishing the activity ends the route. */
class RoutingActivity :
    MainActivity(),
    RoutingController.Container,
    MapManager.StorageCallback {
    private val routing = RoutingController.get()
    private val profiles = listOf(Router.Pedestrian, Router.Bicycle, Router.Vehicle)
    private var origin: MapObject? = null
    private var destination: MapObject? = null
    private var selectedPlace: MapObject? = null
    private var missingMaps = emptyList<String>()
    private var waitingForMaps = false
    private var selectedProfile = -1
    private var buildError: Int? = null
    private var buildProgress = 0
    private var message = R.string.need_endpoints
    private var storageSlot = -1
    private var initialized = false
    private lateinit var status: TextView
    private lateinit var endpoints: TextView
    private lateinit var startButton: Button
    private lateinit var missingMapsView: TextView
    private lateinit var downloadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            origin = BundleCompat.getParcelable(it, "origin", MapObject::class.java)
            destination = BundleCompat.getParcelable(it, "destination", MapObject::class.java)
            missingMaps = it.getStringArrayList("missingMaps").orEmpty()
            waitingForMaps = it.getBoolean("waitingForMaps")
            selectedProfile = it.getInt("profile", -1)
            if (it.containsKey("buildError")) buildError = it.getInt("buildError")
            message = it.getInt("message", R.string.need_endpoints)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCoreInitialized() {
        super.onCoreInitialized()
        val panel = samplePanel()
        panel.addView(TextView(this).apply { setText(R.string.routing_instructions) })
        endpoints = TextView(this).apply { id = R.id.route_endpoints }
        panel.addView(endpoints)
        panel.button(R.string.use_origin) {
            selectedPlace?.let {
                origin = it
                invalidatePlan()
            }
        }
        panel.button(R.string.use_destination) {
            selectedPlace?.let {
                destination = it
                invalidatePlan()
            }
        }
        panel.button(R.string.use_my_position) {
            origin = organicMaps.locationHelper.myPosition
            invalidatePlan()
            if (origin == null) {
                message = R.string.need_position
                updateMenu()
            }
        }
        if (selectedProfile !in profiles.indices) {
            selectedProfile = profiles.indexOf(routing.lastRouterType).coerceAtLeast(0)
        }
        val profile = Spinner(this).apply {
            id = R.id.route_profile
            adapter = ArrayAdapter(
                this@RoutingActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.walking), getString(R.string.cycling), getString(R.string.driving)),
            )
            setSelection(selectedProfile)
        }
        panel.addView(profile)
        profile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != selectedProfile) {
                    selectedProfile = position
                    invalidatePlan()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        panel.button(R.string.build_route, ::buildRoute)
        startButton = panel.button(R.string.start_navigation) {
            if (origin?.isMyPosition == true && LocationUtils.checkLocationPermission(this)) {
                routing.start()
                organicMaps.locationHelper.restartWithNewMode()
            } else {
                message = R.string.need_position_origin
                updateMenu()
            }
        }.apply { id = R.id.start_navigation }
        panel.button(R.string.stop_route) {
            routing.cancel()
            clearBuildState(R.string.route_stopped)
            updateMenu()
        }
        status = TextView(this).apply { id = R.id.route_status }
        panel.addView(status)
        missingMapsView = TextView(this).apply { id = R.id.missing_maps }
        panel.addView(missingMapsView)
        downloadButton = panel.button(R.string.download_missing_maps) {
            message = 0
            waitingForMaps = true
            // Snapshot the IDs because enqueuing can synchronously deliver storage callbacks.
            val requestedMaps = missingMaps
            for (id in requestedMaps) {
                if (!downloadRegion(id)) {
                    waitingForMaps = false
                    message = R.string.storage_full
                    break
                }
            }
            refreshMissingMaps()
        }.apply { id = R.id.download_missing_maps }
        initialized = true
        subscribe()
    }

    private fun buildRoute() {
        val from = origin
        val to = destination
        if (from == null || to == null) {
            message = R.string.need_endpoints
        } else if (!from.isMyPosition && !to.isMyPosition && from.lat == to.lat && from.lon == to.lon) {
            message = R.string.same_endpoints
        } else {
            clearBuildState()
            routing.prepare(from, to, profiles[selectedProfile])
        }
        updateMenu()
    }

    private fun clearBuildState(feedback: Int = 0) {
        missingMaps = emptyList()
        waitingForMaps = false
        buildError = null
        buildProgress = 0
        message = feedback
    }

    private fun subscribe() {
        if (!initialized || storageSlot >= 0) return
        routing.attach(this)
        storageSlot = MapManager.nativeSubscribe(this)
        routing.restore()
        refreshMissingMaps()
    }

    override fun onStart() {
        super.onStart()
        subscribe()
    }

    override fun onStop() {
        if (storageSlot >= 0) {
            // Navigation has no foreground service; planning can remain alive while the activity is stopped.
            if (!isChangingConfigurations && routing.isNavigating) routing.cancel()
            routing.detach()
            MapManager.nativeUnsubscribe(storageSlot)
            storageSlot = -1
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && initialized) routing.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("origin", origin)
        outState.putParcelable("destination", destination)
        outState.putStringArrayList("missingMaps", ArrayList(missingMaps))
        outState.putBoolean("waitingForMaps", waitingForMaps)
        outState.putInt("profile", selectedProfile)
        buildError?.let { outState.putInt("buildError", it) }
        outState.putInt("message", message)
        if (initialized) routing.onSaveState()
        super.onSaveInstanceState(outState)
    }

    override fun onPlacePageActivated(data: PlacePageData) {
        super.onPlacePageActivated(data)
        selectedPlace = data as? MapObject
    }

    override fun onPlacePageDeactivated() {
        super.onPlacePageDeactivated()
        selectedPlace = null
    }

    private fun invalidatePlan() {
        routing.cancel()
        clearBuildState(R.string.inputs_changed)
        updateMenu()
    }

    override fun showRoutePlan(show: Boolean, completionListener: Runnable?) {
        // The controller starts the build from this continuation, even with always-visible controls.
        completionListener?.run()
    }

    override fun updateMenu() {
        if (!initialized) return
        fun title(point: MapObject?) =
            if (point?.isMyPosition == true) getString(R.string.my_position) else point?.title.orEmpty()
        endpoints.text = getString(R.string.route_endpoints, title(origin), title(destination))
        startButton.isEnabled = routing.isBuilt && routing.isPlanning
        val regions = missingMaps.map(CountryItem::fill)
        missingMapsView.visibility = if (regions.isEmpty()) View.GONE else View.VISIBLE
        missingMapsView.text = getString(R.string.missing_maps, regions.joinToString("\n") { it.name })
        downloadButton.visibility = missingMapsView.visibility
        downloadButton.isEnabled = regions.any {
            when (it.status) {
                CountryItem.STATUS_DONE, CountryItem.STATUS_ENQUEUED,
                CountryItem.STATUS_PROGRESS, CountryItem.STATUS_APPLYING,
                -> false

                else -> true
            }
        }
        val failed = regions.firstOrNull { it.status == CountryItem.STATUS_FAILED }
        downloadButton.setText(if (failed != null) R.string.retry_download else R.string.download_missing_maps)
        status.text = when {
            message != 0 -> getString(message)

            waitingForMaps && failed != null -> getString(R.string.download_failed, failed.errorCode)

            waitingForMaps -> getString(
                R.string.download_progress,
                MapManager.nativeGetOverallProgress(missingMaps.toTypedArray()),
            )

            routing.isNavigating -> getString(R.string.navigating)

            routing.isBuilt -> getString(R.string.route_ready)

            routing.isBuilding -> getString(R.string.route_progress, buildProgress)

            buildError != null -> getString(R.string.route_failed, buildError)

            else -> getString(R.string.route_stopped)
        }
    }

    override fun onStartRouteBuilding() {
        clearBuildState()
        updateMenu()
    }

    override fun updateBuildProgress(progress: Int, router: Router) {
        // A final progress update can arrive after the result; controller state determines readiness.
        buildProgress = progress
        updateMenu()
    }

    override fun onBuiltRoute() {
        buildError = null
        message = 0
        updateMenu()
    }

    override fun onNavigationStarted() {
        message = 0
        updateMenu()
    }

    override fun onNavigationCancelled() {
        clearBuildState(R.string.route_stopped)
        updateMenu()
    }

    override fun onPlanningCancelled() = onNavigationCancelled()

    override fun onCommonBuildError(lastResultCode: Int, lastMissingMaps: Array<String>) {
        buildError = lastResultCode
        missingMaps = lastMissingMaps.toList()
        message = 0
        updateMenu()
    }

    override fun onStatusChanged(data: List<MapManager.StorageCallbackData>) = refreshMissingMaps()

    private fun refreshMissingMaps() {
        if (waitingForMaps && missingMaps.isNotEmpty() &&
            missingMaps.all { MapManager.nativeGetStatus(it) == CountryItem.STATUS_DONE }
        ) {
            waitingForMaps = false
            missingMaps = emptyList()
            if (routing.isPlanning) buildRoute()
        }
        updateMenu()
    }

    override fun onProgress(countryId: String, localSize: Long, remoteSize: Long) {
        if (countryId in missingMaps) updateMenu()
    }

    override fun onLocationUpdated(location: Location) {
        if (!routing.isNavigating) return
        if (Framework.nativeIsRouteFinished()) {
            routing.cancel()
            message = R.string.route_arrived
            if (LocationUtils.checkLocationPermission(this)) organicMaps.locationHelper.restartWithNewMode()
            updateMenu()
            return
        }
        val info = Framework.nativeGetRouteFollowingInfo() ?: return
        status.text = resources.getQuantityString(
            R.plurals.navigation_info,
            info.totalTimeInSeconds,
            info.totalTimeInSeconds,
            info.nextStreet,
        )
    }
}
