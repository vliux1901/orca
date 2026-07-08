package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchPlaceholderFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun textInputIsDisplayedAndSubmitted() {
        var submittedQuery: String? = null

        composeRule.setContent {
            MaterialTheme {
                SearchPlaceholderField(
                    onOpenSearch = { query -> submittedQuery = query },
                )
            }
        }

        composeRule
            .onNodeWithTag(SearchPlaceholderFieldTestTag)
            .performTextInput("brakes")

        composeRule
            .onNodeWithTag(SearchPlaceholderFieldTestTag)
            .assertTextContains("brakes")

        composeRule
            .onNodeWithTag(SearchPlaceholderFieldTestTag)
            .performImeAction()

        assertEquals("brakes", submittedQuery)
    }
}
