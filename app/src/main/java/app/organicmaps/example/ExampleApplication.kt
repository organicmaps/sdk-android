package app.organicmaps.example

import android.app.Application
import app.organicmaps.sdk.OrganicMaps

class ExampleApplication : Application() {
    lateinit var organicMaps: OrganicMaps
        private set

    override fun onCreate() {
        super.onCreate()
        // Create the SDK entry point. This is cheap: the heavy native initialization
        // is deferred until OrganicMaps.init() is called (see MainActivity).
        organicMaps = OrganicMaps(
            this,
            "example",
            BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
        )
    }
}
