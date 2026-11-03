package app.organicmaps.example

import android.Manifest
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

internal fun launchSample(): ActivityScenario<MainActivity> {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    for (permission in listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) {
        val command = instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission")
        ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
    }
    val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
    return ActivityScenario.launch<MainActivity>(intent).also { scenario ->
        scenario.await { it.findViewById<View>(R.id.my_position)?.isShown == true }
    }
}

internal fun ActivityScenario<MainActivity>.await(condition: (MainActivity) -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + 15_000
    do {
        var matched = false
        onActivity { matched = condition(it) }
        if (matched) return
        SystemClock.sleep(50)
    } while (SystemClock.uptimeMillis() < deadline)
    throw AssertionError("Sample did not reach the expected UI state")
}

internal fun View.descendants(): Sequence<View> = sequence {
    yield(this@descendants)
    if (this@descendants is ViewGroup) {
        for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }
}

internal fun MainActivity.click(@StringRes label: Int) {
    val button = findViewById<View>(R.id.sample_panel).descendants()
        .filterIsInstance<Button>().first { it.text.toString() == getString(label) }
    assertTrue("${getString(label)} must be enabled", button.isEnabled)
    button.performClick()
}
