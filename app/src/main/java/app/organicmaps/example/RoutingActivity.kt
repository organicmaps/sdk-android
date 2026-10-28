package app.organicmaps.example

import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.location.LocationListener
import app.organicmaps.sdk.location.LocationUtils
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.widget.placepage.PlacePageData

/** Route planning with foreground navigation; route plans survive a stopped activity. */
class RoutingActivity :
    MainActivity(),
    RoutingController.Container,
    LocationListener,
    MapManager.StorageCallback {
    private val routing = RoutingController.get()
    private val profiles = listOf(Router.Pedestrian, Router.Bicycle, Router.Vehicle)
    private var origin: MapObject? = null
    private var destination: MapObject? = null
    private var selectedPlace: MapObject? = null
    private var missingMaps = emptyList<String>()
    private var waitingForMaps = false
    private var savedProfile: Int? = null
    private var missingMapsDialog: AlertDialog? = null
    private var storageSlot = -1
    private var initialized = false
    private lateinit var status: TextView
    private lateinit var endpoints: TextView
    private lateinit var startButton: Button
    private lateinit var profile: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            origin = BundleCompat.getParcelable(it, "origin", MapObject::class.java)
            destination = BundleCompat.getParcelable(it, "destination", MapObject::class.java)
            missingMaps = it.getStringArrayList("missingMaps").orEmpty()
            waitingForMaps = it.getBoolean("waitingForMaps")
            if (it.containsKey("profile")) savedProfile = it.getInt("profile")
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCoreInitialized() {
        super.onCoreInitialized()
        val panel = samplePanel()
        panel.addView(TextView(this).apply { setText(R.string.routing_instructions) })
        endpoints = TextView(this)
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
            if (origin == null) status.setText(R.string.need_position)
        }
        profile = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RoutingActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.walking), getString(R.string.cycling), getString(R.string.driving)),
            )
        }
        panel.addView(profile)
        profile.setSelection(
            savedProfile ?: profiles.indexOf(routing.lastRouterType).coerceAtLeast(0),
        )
        var selectedProfile = profile.selectedItemPosition
        profile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != selectedProfile) {
                    selectedProfile = position
                    invalidatePlan()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        panel.button(R.string.build_route) {
            val from = origin
            val to = destination
            if (from == null || to == null) {
                status.setText(R.string.need_endpoints)
            } else {
                missingMaps = emptyList()
                waitingForMaps = false
                val router = profiles[profile.selectedItemPosition]
                routing.prepare(from, to, router)
            }
        }
        startButton = panel.button(R.string.start_navigation) {
            if (origin?.isMyPosition == true && LocationUtils.checkLocationPermission(this)) {
                routing.start()
                organicMaps.locationHelper.restartWithNewMode()
            } else {
                status.setText(R.string.need_position_origin)
            }
        }
        panel.button(R.string.stop_route) {
            missingMaps = emptyList()
            waitingForMaps = false
            routing.cancel()
        }
        status = TextView(this)
        panel.addView(status)
        initialized = true
        subscribe()
        showEndpoints()
    }

    private fun subscribe() {
        if (!initialized || storageSlot >= 0) return
        routing.attach(this)
        storageSlot = MapManager.nativeSubscribe(this)
        organicMaps.locationHelper.addListener(this)
        routing.restore()
        refreshMissingMaps()
        updateMenu()
    }

    override fun onStart() {
        super.onStart()
        subscribe()
    }

    override fun onStop() {
        if (storageSlot >= 0) {
            // This sample deliberately provides navigation only while visible; no foreground service is installed.
            if (!isChangingConfigurations && routing.isNavigating) routing.cancel()
            missingMapsDialog?.dismiss()
            missingMapsDialog = null
            routing.detach()
            organicMaps.locationHelper.removeListener(this)
            MapManager.nativeUnsubscribe(storageSlot)
            storageSlot = -1
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("origin", origin)
        outState.putParcelable("destination", destination)
        outState.putStringArrayList("missingMaps", ArrayList(missingMaps))
        outState.putBoolean("waitingForMaps", waitingForMaps)
        if (initialized) outState.putInt("profile", profile.selectedItemPosition)
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

    private fun showEndpoints() {
        fun title(point: MapObject?) =
            if (point?.isMyPosition == true) getString(R.string.my_position) else point?.title.orEmpty()
        endpoints.text = getString(R.string.route_endpoints, title(origin), title(destination))
    }

    private fun invalidatePlan() {
        missingMaps = emptyList()
        waitingForMaps = false
        routing.cancel()
        showEndpoints()
        updateMenu()
        status.setText(R.string.inputs_changed)
    }

    override fun showRoutePlan(show: Boolean, completionListener: Runnable?) {
        // Our controls are already present. The controller starts the build from this completion callback.
        completionListener?.run()
    }

    override fun updateMenu() {
        startButton.isEnabled = routing.isBuilt && routing.isPlanning
    }

    override fun onStartRouteBuilding() {
        status.setText(R.string.building_route)
        updateMenu()
    }

    override fun updateBuildProgress(progress: Int, router: Router) {
        // A final progress callback can follow the build result; controller state owns readiness.
        when {
            routing.isNavigating -> status.setText(R.string.navigating)
            routing.isBuilt -> status.setText(R.string.route_ready)
            routing.isBuilding -> status.text = getString(R.string.route_progress, progress)
        }
    }

    override fun onBuiltRoute() {
        status.setText(R.string.route_ready)
        updateMenu()
    }

    override fun onNavigationStarted() {
        status.setText(R.string.navigating)
        updateMenu()
    }

    override fun onNavigationCancelled() {
        status.setText(R.string.route_stopped)
        updateMenu()
    }

    override fun onPlanningCancelled() = onNavigationCancelled()

    override fun onCommonBuildError(lastResultCode: Int, lastMissingMaps: Array<String>) {
        updateMenu()
        status.text = getString(R.string.route_failed, lastResultCode)
        missingMaps = lastMissingMaps.toList()
        if (missingMaps.isEmpty() || waitingForMaps) return
        missingMapsDialog?.dismiss()
        missingMapsDialog = AlertDialog.Builder(this)
            .setTitle(R.string.missing_maps)
            .setMessage(missingMaps.joinToString("\n") { MapManager.nativeGetName(it) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.download_region) { _, _ ->
                waitingForMaps = true
                missingMaps.forEach { id ->
                    when (CountryItem.fill(id).status) {
                        CountryItem.STATUS_UPDATABLE -> MapManager.startUpdate(id)
                        CountryItem.STATUS_FAILED -> MapManager.retryDownload(id)
                        else -> MapManager.startDownload(id)
                    }
                }
                refreshMissingMaps()
            }
            .show()
    }

    override fun onStatusChanged(data: List<MapManager.StorageCallbackData>) = refreshMissingMaps()

    private fun refreshMissingMaps() {
        if (!waitingForMaps || missingMaps.isEmpty()) return
        val failed = missingMaps.map(CountryItem::fill).firstOrNull { it.status == CountryItem.STATUS_FAILED }
        if (failed != null) status.text = getString(R.string.download_failed, failed.errorCode)
        if (missingMaps.isNotEmpty() && missingMaps.all {
                CountryItem.fill(it).status == CountryItem.STATUS_DONE
            }
        ) {
            missingMaps = emptyList()
            waitingForMaps = false
            if (routing.isPlanning) routing.rebuildLastRoute()
        }
    }

    override fun onProgress(countryId: String, localSize: Long, remoteSize: Long) {
        if (countryId in missingMaps && remoteSize > 0) {
            status.text = getString(R.string.download_progress, localSize * 100 / remoteSize)
        }
    }

    override fun onLocationUpdated(location: Location) {
        if (!routing.isNavigating) return
        val info = Framework.nativeGetRouteFollowingInfo() ?: return
        status.text = getString(R.string.navigation_info, info.totalTimeInSeconds, info.nextStreet)
    }
}
