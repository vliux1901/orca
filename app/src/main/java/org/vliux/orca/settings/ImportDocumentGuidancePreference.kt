package org.vliux.orca.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ImportDocumentGuidancePreference {
    fun shouldShowGuidance(): Boolean

    fun disableGuidance()
}

@Singleton
class SharedPreferencesImportDocumentGuidancePreference @Inject constructor(
    @ApplicationContext context: Context,
) : ImportDocumentGuidancePreference {
    private val state = ImportDocumentGuidanceState(
        preferenceStore = SharedPreferencesBooleanPreferenceStore(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    override fun shouldShowGuidance(): Boolean = state.shouldShowGuidance()

    override fun disableGuidance() {
        state.disableGuidance()
    }
}

internal class ImportDocumentGuidanceState(
    private val preferenceStore: BooleanPreferenceStore,
) {
    fun shouldShowGuidance(): Boolean = preferenceStore.getBoolean(KEY_SHOW_IMPORT_GUIDANCE, true)

    fun disableGuidance() {
        preferenceStore.putBoolean(KEY_SHOW_IMPORT_GUIDANCE, false)
    }
}

internal interface BooleanPreferenceStore {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)
}

internal class SharedPreferencesBooleanPreferenceStore(
    private val preferences: SharedPreferences,
) : BooleanPreferenceStore {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = preferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}

private const val PREFERENCES_NAME = "import_document_guidance_preferences"
private const val KEY_SHOW_IMPORT_GUIDANCE = "show_import_document_guidance"
