package app.organicmaps.example

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.organicmaps.sdk.OrganicMaps
import java.io.IOException

class ExampleApplication : Application() {
    private val mutableReady = MutableLiveData(false)
    val ready: LiveData<Boolean> = mutableReady

    lateinit var organicMaps: OrganicMaps
        private set

    override fun onCreate() {
        super.onCreate()
        // Keep SDK ownership in the application so callbacks cannot retain an activity.
        organicMaps = OrganicMaps(
            this,
            "example",
            BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
        )
    }

    /** Keep loading state in the process owner so activity recreation cannot skip a pending load. */
    @Throws(IOException::class)
    fun initialize() {
        organicMaps.init { mutableReady.value = true }
    }
}
