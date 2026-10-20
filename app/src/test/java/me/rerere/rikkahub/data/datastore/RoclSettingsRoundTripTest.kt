package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlin.uuid.Uuid
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoclSettingsRoundTripTest {
    @Test
    fun forkSettingsSurvivePreferencesRoundTrip() {
        val settings = Settings(
            subAgentModelId = Uuid.parse("00000000-0000-0000-0000-000000000123"),
            subAgentReasoningLevel = ReasoningLevel.HIGH,
            groupChatTemplates = listOf(GroupChatTemplate(name = "Group")),
            advancedAppearanceSetting = AdvancedAppearanceSetting(composerOpacity = 0.4f),
            displaySetting = DisplaySetting(inlineWorkspaceReadEnabled = true),
        )
        val restored = mutablePreferencesOf().apply { putSettings(settings) }.toSettings()
        assertEquals(settings.subAgentModelId, restored.subAgentModelId)
        assertEquals(settings.subAgentReasoningLevel, restored.subAgentReasoningLevel)
        assertEquals(settings.groupChatTemplates, restored.groupChatTemplates)
        assertEquals(settings.advancedAppearanceSetting, restored.advancedAppearanceSetting)
        assertEquals(settings.displaySetting, restored.displaySetting)
    }

    @Test
    fun appearanceSurvivesFullBackupSerialization() {
        val settings = Settings(advancedAppearanceSetting = AdvancedAppearanceSetting(composerOpacity = 0.4f))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings))
        assertEquals(settings.advancedAppearanceSetting, restored.advancedAppearanceSetting)
    }

    @Test
    fun clearingSubagentModelKeepsItDisabled() {
        val settings = Settings(subAgentModelId = Uuid.random())
        val preferences = mutablePreferencesOf().apply { putSettings(settings) }
        preferences.putSettings(settings.copy(subAgentModelId = null), persisted = settings)
        assertNull(preferences.toSettings().subAgentModelId)
    }
}
