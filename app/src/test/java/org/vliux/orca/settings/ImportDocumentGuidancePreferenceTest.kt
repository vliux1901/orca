package org.vliux.orca.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDocumentGuidancePreferenceTest {
    @Test
    fun guidanceIsShownByDefault() {
        val state = ImportDocumentGuidanceState(FakeBooleanPreferenceStore())

        assertTrue(state.shouldShowGuidance())
    }

    @Test
    fun disablingGuidancePersistsOptOut() {
        val store = FakeBooleanPreferenceStore()
        val state = ImportDocumentGuidanceState(store)

        state.disableGuidance()

        assertFalse(state.shouldShowGuidance())
        assertEquals(false, store.values["show_import_document_guidance"])
    }
}

private class FakeBooleanPreferenceStore : BooleanPreferenceStore {
    val values = mutableMapOf<String, Boolean>()

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] ?: defaultValue

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
}
