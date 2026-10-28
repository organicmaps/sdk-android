package app.organicmaps.example

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.organicmaps.sdk.OrganicMaps
import java.io.IOException

class ExampleApplication : Application() {
    private val mutableReady = MutableLiveData(false)
    val ready: LiveData<Boolean> = mutableReady
    private var initializationStarted = false

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

    /** Keep loading state in the process owner so activity recreation cannot skip a pending load. */
    @Throws(IOException::class)
    fun initialize() {
        if (initializationStarted) return
        initializationStarted = true
        try {
            organicMaps.init { mutableReady.value = true }
        } catch (error: IOException) {
            initializationStarted = false
            throw error
        }
    }
}
