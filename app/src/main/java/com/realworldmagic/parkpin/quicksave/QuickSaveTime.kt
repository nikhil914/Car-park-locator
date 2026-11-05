package com.realworldmagic.parkpin.quicksave

import android.content.Context
import android.text.format.DateFormat
import com.realworldmagic.parkpin.R
import java.util.Date

internal object QuickSaveTime {
    /** "Saved just now" / "Saved 12 min ago" / "Saved 3 h ago" / "Saved 4 Nov". */
    fun savedAgo(context: Context, savedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val minutes = ((nowMs - savedAtMs).coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> context.getString(R.string.quicksave_saved_just_now)
            minutes < 60 -> context.getString(R.string.quicksave_saved_min_ago, minutes.toInt())
            minutes < 24 * 60 -> context.getString(R.string.quicksave_saved_h_ago, (minutes / 60).toInt())
            else -> context.getString(
                R.string.quicksave_saved_on,
                DateFormat.getMediumDateFormat(context).format(Date(savedAtMs)),
            )
        }
    }

    /** Locale-aware clock time, e.g. "14:05" or "2:05 PM". */
    fun clock(context: Context, timeMs: Long): String = DateFormat.getTimeFormat(context).format(Date(timeMs))
}
