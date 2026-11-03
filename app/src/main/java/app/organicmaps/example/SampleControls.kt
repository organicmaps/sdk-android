package app.organicmaps.example

import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.StringRes
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager

internal fun LinearLayout.button(@StringRes label: Int, action: () -> Unit): Button = Button(context).also { button ->
    button.setText(label)
    button.setOnClickListener { action() }
    addView(button)
}

/** Returns false when the operation's storage preflight fails; native callbacks report transfer errors. */
internal fun downloadRegion(id: String): Boolean {
    when (CountryItem.fill(id).status) {
        CountryItem.STATUS_DONE, CountryItem.STATUS_ENQUEUED,
        CountryItem.STATUS_PROGRESS, CountryItem.STATUS_APPLYING,
        -> Unit

        CountryItem.STATUS_UPDATABLE -> {
            if (!MapManager.nativeHasSpaceToUpdate(id)) return false
            MapManager.startUpdate(id)
        }

        // The downloader retains whether each failed transfer was a download or an update.
        CountryItem.STATUS_FAILED -> MapManager.retryDownload(id)

        else -> {
            if (!MapManager.nativeHasSpaceToDownloadCountry(id)) return false
            MapManager.startDownload(id)
        }
    }
    return true
}
