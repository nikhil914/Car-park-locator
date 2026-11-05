package com.realworldmagic.parkpin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.realworldmagic.parkpin.ui.navigation.ParkPinNavHost
import com.realworldmagic.parkpin.ui.theme.ParkPinTheme

class MainActivity : ComponentActivity() {

    /** Spot id delivered via notification / widget intent extra "spotId"; opens the guide. */
    private var pendingSpotId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingSpotId = intent.spotIdExtra()
        setContent {
            ParkPinTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ParkPinNavHost(
                        pendingGuideSpotId = pendingSpotId,
                        onPendingConsumed = { pendingSpotId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.spotIdExtra()?.let { pendingSpotId = it }
    }

    private fun Intent?.spotIdExtra(): Long? {
        if (this == null || !hasExtra(EXTRA_SPOT_ID)) return null
        // Accept Long or Int extras defensively.
        val asLong = getLongExtra(EXTRA_SPOT_ID, -1L)
        val id = if (asLong != -1L) asLong else getIntExtra(EXTRA_SPOT_ID, -1).toLong()
        return id.takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_SPOT_ID = "spotId"
    }
}
