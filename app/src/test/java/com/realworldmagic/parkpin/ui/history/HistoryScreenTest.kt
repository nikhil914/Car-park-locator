package com.realworldmagic.parkpin.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.testutil.TestSpots.spot
import com.realworldmagic.parkpin.ui.theme.ParkPinTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: ParkPinApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // HistoryViewModel's factory pulls MeterReminderScheduler, which needs WorkManager.
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun emptyHistory_showsEmptyState() {
        compose.setContent { ParkPinTheme(dynamicColor = false) { HistoryScreen(onGuide = {}) } }

        waitForText("No parking history yet")
        compose.onNodeWithText("No parking history yet").assertIsDisplayed()
        compose.onNodeWithText("Spots you save appear here so you can find your way back again.").assertIsDisplayed()
    }

    @Test
    fun savedSpot_isListedAndCanBeDeleted() {
        runBlocking {
            app.container.parkingRepository.saveNewSpot(spot(savedAtMs = 1_700_000_000_000, note = "Row C", floorLabel = "P2"))
        }
        var guided: Long? = null
        compose.setContent { ParkPinTheme(dynamicColor = false) { HistoryScreen(onGuide = { guided = it }) } }

        waitForText("Row C · Floor P2")
        compose.onNodeWithText("Active").assertIsDisplayed()

        compose.onNodeWithText("Row C · Floor P2").performClick()
        compose.runOnIdle { assertEquals(true, guided != null) }

        compose.onAllNodesWithContentDescription("Delete spot").onFirst().performClick()
        compose.onNodeWithText("Delete this spot?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()

        waitForText("No parking history yet")
    }
}
