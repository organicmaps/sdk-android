package app.organicmaps.example

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import app.organicmaps.sdk.routing.LaneInfo
import app.organicmaps.sdk.routing.LaneWay
import app.organicmaps.sdk.widgets.lanes.LanesView
import app.organicmaps.sdk.widgets.speedlimit.SpeedLimitView

/** A standalone host for the published SDK modules and navigation widget previews. */
class ConsumerActivity : MainActivity() {
    override fun onCoreInitialized() {
        super.onCoreInitialized()
        val panel = samplePanel()
        panel.addView(TextView(this).apply { setText(R.string.consumer_widgets) })
        val widgetSize = (80 * resources.displayMetrics.density).toInt()
        panel.addView(
            LanesView(this, null).apply {
                id = R.id.consumer_lanes
                setLanes(arrayOf(LaneInfo(arrayOf(LaneWay.Left, LaneWay.Through), LaneWay.Through)))
            },
            LinearLayout.LayoutParams(MATCH_PARENT, widgetSize),
        )
        panel.addView(
            SpeedLimitView(this).apply {
                id = R.id.consumer_speed_limit
                setSpeedLimit(50, false)
            },
            LinearLayout.LayoutParams(widgetSize, widgetSize),
        )
    }

    override fun onRenderingInitializationFinished() {
        // The external probe observes the native callback through a platform View, without naming host methods.
        findViewById<View>(R.id.map).tag = true
    }
}
