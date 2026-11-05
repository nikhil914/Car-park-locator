package com.realworldmagic.parkpin.ui.components

import android.Manifest
import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.realworldmagic.parkpin.ui.theme.ParkPinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PermissionGateTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun setGate(permissions: List<String> = LocationPermissions, anyGranted: Boolean = true) {
        compose.setContent {
            ParkPinTheme(dynamicColor = false) {
                PermissionGate(
                    permissions = permissions,
                    title = "Location needed",
                    rationale = "ParkPin uses GPS to remember where you parked.",
                    anyGranted = anyGranted,
                ) { Text("Protected content") }
            }
        }
    }

    @Test
    fun denied_showsRationaleAndGrantButton() {
        setGate()
        compose.onNodeWithText("Location needed").assertIsDisplayed()
        compose.onNodeWithText("ParkPin uses GPS to remember where you parked.").assertIsDisplayed()
        compose.onNodeWithText("Grant permission").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Protected content").assertDoesNotExist()
        compose.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test
    fun granted_showsContent() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        setGate()
        compose.onNodeWithText("Protected content").assertIsDisplayed()
        compose.onNodeWithText("Grant permission").assertDoesNotExist()
    }

    @Test
    fun anyGranted_coarseOnlyIsEnough() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        setGate(anyGranted = true)
        compose.onNodeWithText("Protected content").assertIsDisplayed()
    }

    @Test
    fun allRequired_partialGrantStillGated() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        setGate(anyGranted = false)
        compose.onNodeWithText("Grant permission").assertIsDisplayed()
        compose.onNodeWithText("Protected content").assertDoesNotExist()
    }
}
