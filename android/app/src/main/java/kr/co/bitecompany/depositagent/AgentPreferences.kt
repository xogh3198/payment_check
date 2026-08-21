package kr.co.bitecompany.depositagent

import android.content.Context
import java.util.UUID

class AgentPreferences(context: Context) {
    private val storage = SecureStorage(context, PREFERENCES_NAME, KEYSTORE_ALIAS)

    var serverBaseUrl: String
        get() = storage.getString(KEY_SERVER_BASE_URL)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SERVER_BASE_URL
        set(value) = storage.putString(KEY_SERVER_BASE_URL, normalizeBaseUrl(value))

    val agentId: String?
        get() = storage.getString(KEY_AGENT_ID)

    val agentSecret: String?
        get() = storage.getString(KEY_AGENT_SECRET)

    val eventPath: String
        get() = storage.getString(KEY_EVENT_PATH) ?: DEFAULT_EVENT_PATH

    val heartbeatPath: String
        get() = storage.getString(KEY_HEARTBEAT_PATH) ?: DEFAULT_HEARTBEAT_PATH

    val isEnrolled: Boolean
        get() = !agentId.isNullOrBlank() && !agentSecret.isNullOrBlank()

    val installationId: String
        get() {
            val existing = storage.getString(KEY_INSTALLATION_ID)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            storage.putString(KEY_INSTALLATION_ID, created)
            return created
        }

    fun saveEnrollment(
        serverBaseUrl: String,
        agentId: String,
        agentSecret: String,
        eventPath: String,
        heartbeatPath: String,
    ) {
        storage.putString(KEY_SERVER_BASE_URL, normalizeBaseUrl(serverBaseUrl))
        storage.putString(KEY_AGENT_ID, agentId)
        storage.putString(KEY_AGENT_SECRET, agentSecret)
        storage.putString(KEY_EVENT_PATH, eventPath)
        storage.putString(KEY_HEARTBEAT_PATH, heartbeatPath)
    }

    fun clearEnrollment() {
        storage.remove(KEY_AGENT_ID, KEY_AGENT_SECRET, KEY_EVENT_PATH, KEY_HEARTBEAT_PATH)
    }

    fun endpoint(path: String): String = "${serverBaseUrl}${normalizePath(path)}"

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://seller-api.marketbite.co.kr"
        private const val PREFERENCES_NAME = "deposit_agent_preferences"
        private const val KEYSTORE_ALIAS = "marketbite_payment_agent_preferences"
        private const val KEY_SERVER_BASE_URL = "server_base_url"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_AGENT_SECRET = "agent_secret"
        private const val KEY_EVENT_PATH = "event_path"
        private const val KEY_HEARTBEAT_PATH = "heartbeat_path"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val DEFAULT_EVENT_PATH = "/api/v1/payment-agents/events"
        private const val DEFAULT_HEARTBEAT_PATH = "/api/v1/payment-agents/heartbeat"

        fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')

        private fun normalizePath(value: String): String = if (value.startsWith('/')) value else "/$value"
    }
}
