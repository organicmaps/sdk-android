package app.organicmaps.example

import android.Manifest
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.Map as OMMap
import app.organicmaps.sdk.MapController
import app.organicmaps.sdk.MapRenderingListener
import app.organicmaps.sdk.MapStyle
import app.organicmaps.sdk.OrganicMaps
import app.organicmaps.sdk.PlacePageActivationListener
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.location.LocationState
import app.organicmaps.sdk.location.LocationUtils
import app.organicmaps.sdk.widget.placepage.PlacePageData
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.Locale

/**
 * A minimal Organic Maps SDK integration:
 * - initializes the native core,
 * - shows an interactive offline world map,
 * - implements the "my position" button with runtime permissions,
 * - shows info about tapped places.
 *
 * The SDK class [app.organicmaps.sdk.Map] is imported as `OMMap`
 * to avoid clashing with [kotlin.collections.Map].
 */
class MainActivity :
    AppCompatActivity(),
    MapRenderingListener,
    PlacePageActivationListener,
    LocationState.ModeChangeListener {
    private lateinit var organicMaps: OrganicMaps
    private var mapController: MapController? = null
    private lateinit var myPositionFab: FloatingActionButton
    private var placeSnackbar: Snackbar? = null

    private val locationPermissionsRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), ::onLocationPermissionsResult)

    // Closes the selected place on the system back gesture; enabled only while a place is selected.
    private val closePlacePageOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Triggers onPlacePageDeactivated() from the native code.
            Framework.nativeDeactivatePopup()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        organicMaps = (application as ExampleApplication).organicMaps

        // Initialize the native core: create platform directories and load bundled maps.
        // The callback is invoked on the main thread when maps are loaded. On the second
        // and subsequent calls (e.g. on activity recreation) init() returns false and
        // the callback is not invoked because everything is initialized already.
        val callbackWillFire = try {
            organicMaps.init(::onCoreInitialized)
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.init_failed, e.message), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!callbackWillFire) {
            onCoreInitialized()
        }
    }

    private fun onCoreInitialized() {
        if (isFinishing || isDestroyed) {
            return
        }

        // Match the map style to the system theme. Before the rendering engine is
        // created (the first launch), the style is only marked to avoid extra work;
        // on activity recreation (e.g. a theme change) the engine is still alive
        // and the new style must be applied explicitly.
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val mapStyle = if (isNightMode) MapStyle.Dark else MapStyle.Clear
        if (OMMap.isEngineCreated()) {
            MapStyle.set(mapStyle)
        } else {
            MapStyle.mark(mapStyle)
        }

        setContentView(R.layout.activity_main)

        // MapController binds the MapView to the activity lifecycle and manages the rendering
        // engine. The last argument is launchByDeepLink; the example does not handle deep links.
        val controller =
            MapController(findViewById(R.id.map), organicMaps.locationHelper, this, null, false)
        lifecycle.addObserver(controller)
        mapController = controller

        myPositionFab = findViewById(R.id.my_position)
        // Cycles the location mode: NOT_FOLLOW -> FOLLOW -> FOLLOW_AND_ROTATE -> ...
        // The current mode is delivered to onMyPositionModeChanged().
        myPositionFab.setOnClickListener { LocationState.nativeSwitchToNextMode() }
        findViewById<View>(R.id.zoom_in).setOnClickListener { OMMap.zoomIn() }
        findViewById<View>(R.id.zoom_out).setOnClickListener { OMMap.zoomOut() }

        // The map is rendered edge-to-edge: move the built-in map widgets (compass,
        // ruler, copyright) and our buttons out of the system bars and display cutouts.
        val mapButtons = findViewById<View>(R.id.map_buttons)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val fabMargin = resources.getDimensionPixelOffset(R.dimen.fab_margin)
            (mapButtons.layoutParams as ViewGroup.MarginLayoutParams).apply {
                rightMargin = fabMargin + insets.right
                bottomMargin = fabMargin + insets.bottom
            }
            mapButtons.requestLayout()
            mapController?.updateCompassOffset(insets.right, insets.top)
            mapController?.updateBottomWidgetsOffset(insets.left, insets.bottom)
            windowInsets
        }

        onBackPressedDispatcher.addCallback(this, closePlacePageOnBack)
        Framework.nativePlacePageActivationListener(this)
        LocationState.nativeSetListener(this)
    }

    override fun onResume() {
        super.onResume()
        // Restart location updates when returning to the foreground (no-op before
        // the rendering engine is created or when updates are already active).
        if (mapController != null) {
            organicMaps.locationHelper.resumeLocationInForeground()
        }
    }

    override fun onStop() {
        super.onStop()
        // Save the battery when the app is in the background.
        if (mapController != null && !isChangingConfigurations) {
            organicMaps.locationHelper.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mapController != null) {
            LocationState.nativeRemoveListener()
            Framework.nativeRemovePlacePageActivationListener(this)
        }
    }

    /**
     * Called by the native code whenever the location mode changes: on
     * nativeSwitchToNextMode() and when the rendering engine starts.
     * This is the place to start/stop the location updates and update the button state.
     */
    override fun onMyPositionModeChanged(newMode: Int) {
        myPositionFab.setImageResource(
            when (newMode) {
                LocationState.FOLLOW, LocationState.FOLLOW_AND_ROTATE -> R.drawable.ic_my_location
                else -> R.drawable.ic_location_searching
            },
        )

        val locationHelper = organicMaps.locationHelper
        if (newMode == LocationState.NOT_FOLLOW_NO_POSITION) {
            // Location was switched off by the user.
            if (locationHelper.isActive) {
                locationHelper.stop()
            }
            return
        }

        if (!LocationUtils.checkLocationPermission(this)) {
            // Resets the mode to NOT_FOLLOW_NO_POSITION (calls this listener again).
            LocationState.nativeOnLocationError(LocationState.ERROR_DENIED)
            locationPermissionsRequest.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
            return
        }

        locationHelper.restartWithNewMode()
    }

    private fun onLocationPermissionsResult(grants: Map<String, Boolean>) {
        if (!LocationUtils.checkLocationPermission(this)) {
            Snackbar.make(findViewById(R.id.root), R.string.location_denied, Snackbar.LENGTH_LONG).show()
            return
        }
        if (LocationState.getMode() == LocationState.NOT_FOLLOW_NO_POSITION) {
            LocationState.nativeSwitchToNextMode()
        }
    }

    // Called by the native code when a place on the map is selected (tapped).
    override fun onPlacePageActivated(data: PlacePageData) {
        val place = data as? MapObject ?: return
        val coordinates = String.format(Locale.US, "%.5f, %.5f", place.lat, place.lon)
        val description = listOf(place.title, place.subtitle, coordinates)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        closePlacePageOnBack.isEnabled = true
        placeSnackbar = Snackbar.make(findViewById(R.id.root), description, Snackbar.LENGTH_INDEFINITE)
            .setTextMaxLines(3)
            .setAnchorView(findViewById(R.id.map_buttons))
            .also { it.show() }
    }

    // Called by the native code when the selection is cleared (a tap on an empty map area).
    override fun onPlacePageDeactivated() {
        closePlacePageOnBack.isEnabled = false
        placeSnackbar?.dismiss()
        placeSnackbar = null
    }
}
