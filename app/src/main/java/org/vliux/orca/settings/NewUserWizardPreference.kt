package org.vliux.orca.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface NewUserWizardPreference {
    fun shouldShowWizard(): Boolean

    fun acknowledgeWizard()
}

@Singleton
class SharedPreferencesNewUserWizardPreference @Inject constructor(
    @ApplicationContext context: Context,
) : NewUserWizardPreference {
    private val state = NewUserWizardState(
        preferenceStore = SharedPreferencesBooleanPreferenceStore(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    override fun shouldShowWizard(): Boolean = state.shouldShowWizard()

    override fun acknowledgeWizard() {
        state.acknowledgeWizard()
    }
}

internal class NewUserWizardState(
    private val preferenceStore: BooleanPreferenceStore,
) {
    fun shouldShowWizard(): Boolean = preferenceStore.getBoolean(KEY_SHOW_NEW_USER_WIZARD, true)

    fun acknowledgeWizard() {
        preferenceStore.putBoolean(KEY_SHOW_NEW_USER_WIZARD, false)
    }
}

private const val PREFERENCES_NAME = "new_user_wizard_preferences"
private const val KEY_SHOW_NEW_USER_WIZARD = "show_new_user_wizard"
