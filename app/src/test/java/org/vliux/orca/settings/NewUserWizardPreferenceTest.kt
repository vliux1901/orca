package org.vliux.orca.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewUserWizardPreferenceTest {
    @Test
    fun wizardIsShownByDefault() {
        val state = NewUserWizardState(FakeBooleanPreferenceStore())

        assertTrue(state.shouldShowWizard())
    }

    @Test
    fun acknowledgingWizardDisablesFutureAutoLaunch() {
        val store = FakeBooleanPreferenceStore()
        val state = NewUserWizardState(store)

        state.acknowledgeWizard()

        assertFalse(state.shouldShowWizard())
        assertEquals(false, store.values["show_new_user_wizard"])
    }
}
