package kr.co.bitecompany.depositagent

import android.content.Context
import org.json.JSONArray

class EventStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun add(event: DepositEvent): Boolean {
        val events = all().toMutableList()
        if (events.any { it.id == event.id }) return false
        events.add(0, event)
        persist(events.take(MAX_EVENTS))
        return true
    }

    @Synchronized
    fun all(): List<DepositEvent> {
        val raw = preferences.getString(KEY_EVENTS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(DepositEvent.fromStorageJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun updateDelivery(
        eventId: String,
        status: String,
        httpStatus: Int?,
        error: String?,
        deliveredAt: String?,
    ) {
        val updated = all().map { event ->
            if (event.id != eventId) event else event.copy(
                deliveryStatus = status,
                deliveryAttempts = event.deliveryAttempts + 1,
                lastHttpStatus = httpStatus,
                lastError = error,
                deliveredAt = deliveredAt,
            )
        }
        persist(updated)
    }

    @Synchronized
    fun clear() {
        preferences.edit().putString(KEY_EVENTS, "[]").apply()
    }

    private fun persist(events: List<DepositEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toStorageJson()) }
        preferences.edit().putString(KEY_EVENTS, array.toString()).commit()
    }

    companion object {
        private const val PREFERENCES_NAME = "deposit_agent_events"
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 100
    }
}
