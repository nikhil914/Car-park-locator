package com.realworldmagic.parkpin.quicksave

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Invisible (translucent, no UI) activity launched from the Quick Settings tile. Because it is a
 * visible activity at the moment it calls startForegroundService(), [QuickSaveService] is
 * guaranteed while-in-use location access on every API level, including Android 14+ where a
 * location FGS created from the background throws SecurityException. Shows over the lock screen
 * so the user can save without unlocking. Finishes immediately.
 *
 * Manifest: exported=false, theme @android:style/Theme.Translucent.NoTitleBar, excludeFromRecents,
 * noHistory, taskAffinity="" (see docs/QUICKSAVE.md).
 */
class QuickSaveTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        if (savedInstanceState == null) {
            val source = intent?.getStringExtra(QuickSaveService.EXTRA_SOURCE) ?: QuickSaveService.SOURCE_TILE
            QuickSaveService.start(this, source)
        }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        fun intent(context: Context, source: String): Intent =
            Intent(context, QuickSaveTrampolineActivity::class.java)
                .putExtra(QuickSaveService.EXTRA_SOURCE, source)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
    }
}
