package com.realworldmagic.parkpin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.realworldmagic.parkpin.ui.theme.ParkPinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectionArrowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersAtDefaultSize() {
        compose.setContent {
            ParkPinTheme(dynamicColor = false) { DirectionArrow(rotationDeg = 45f, modifier = Modifier.testTag("arrow")) }
        }
        compose.onNodeWithTag("arrow")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(240.dp)
            .assertHeightIsEqualTo(240.dp)
    }

    @Test
    fun honoursCustomSizeAndNullRotation() {
        compose.setContent {
            ParkPinTheme(dynamicColor = false) { DirectionArrow(rotationDeg = null, size = 120.dp, modifier = Modifier.testTag("arrow")) }
        }
        compose.onNodeWithTag("arrow").assertWidthIsEqualTo(120.dp)
    }

    /** The arrow is decorative; callers (GuideScreen) supply the spoken description. */
    @Test
    fun hasNoOwnContentDescription() {
        compose.setContent {
            ParkPinTheme(dynamicColor = false) { DirectionArrow(rotationDeg = 90f, modifier = Modifier.testTag("arrow")) }
        }
        compose.onNodeWithTag("arrow")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun parentDescriptionIsExposedAndRotationChangesDoNotCrash() {
        var rotation by mutableStateOf<Float?>(10f)
        compose.setContent {
            ParkPinTheme(dynamicColor = false) {
                Column(Modifier.clearAndSetSemantics { contentDescription = "Car at 2 o'clock, 40 m" }) {
                    DirectionArrow(rotationDeg = rotation)
                }
            }
        }
        compose.onNodeWithContentDescription("Car at 2 o'clock, 40 m").assertIsDisplayed()

        // Wrap-around and loss of heading.
        compose.runOnIdle { rotation = 350f }
        compose.waitForIdle()
        compose.runOnIdle { rotation = null }
        compose.waitForIdle()
        compose.runOnIdle { rotation = -170f }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Car at 2 o'clock, 40 m").assertIsDisplayed()
    }
}
