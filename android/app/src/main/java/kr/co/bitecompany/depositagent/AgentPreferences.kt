package kr.co.bitecompany.depositagent

import android.content.Context
import java.util.UUID

class AgentPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = preferences.getString(KEY_WEBHOOK_URL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var webhookSecret: String
        get() = preferences.getString(KEY_WEBHOOK_SECRET, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_WEBHOOK_SECRET, value).apply()

    val deviceId: String
        get() {
            val existing = preferences.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            preferences.edit().putString(KEY_DEVICE_ID, created).commit()
            return created
        }

    companion object {
        private const val PREFERENCES_NAME = "deposit_agent_preferences"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_WEBHOOK_SECRET = "webhook_secret"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
