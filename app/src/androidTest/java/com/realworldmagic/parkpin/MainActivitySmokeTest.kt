package com.realworldmagic.parkpin

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app on a device/emulator and checks the Compose UI comes up. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsComposeContent() {
        compose.onRoot().assertExists()
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            assertTrue(activity.application is ParkPinApp)
        }
    }
}
