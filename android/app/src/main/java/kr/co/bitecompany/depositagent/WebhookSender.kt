package kr.co.bitecompany.depositagent

import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookSender {
    const val ACTION_EVENTS_CHANGED = "kr.co.bitecompany.depositagent.EVENTS_CHANGED"

    private val executor = Executors.newSingleThreadExecutor()

    fun send(context: Context, event: DepositEvent) {
        val appContext = context.applicationContext
        val preferences = AgentPreferences(appContext)
        val url = preferences.webhookUrl
        if (url.isBlank()) return

        executor.execute {
            val store = EventStore(appContext)
            var connection: HttpURLConnection? = null
            try {
                val payload = event.toWebhookJson().toString()
                val timestamp = Instant.now().epochSecond.toString()
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("X-Deposit-Agent-Id", event.deviceId)
                connection.setRequestProperty("X-Deposit-Agent-Timestamp", timestamp)
                if (preferences.webhookSecret.isNotBlank()) {
                    connection.setRequestProperty(
                        "X-Deposit-Agent-Signature",
                        hmacSha256(preferences.webhookSecret, "$timestamp.$payload"),
                    )
                }

                connection.outputStream.use { output ->
                    output.write(payload.toByteArray(StandardCharsets.UTF_8))
                }

                val status = connection.responseCode
                val responseText = readResponse(connection)
                if (status in 200..299) {
                    store.updateDelivery(
                        eventId = event.id,
                        status = "DELIVERED",
                        httpStatus = status,
                        error = null,
                        deliveredAt = Instant.now().toString(),
                    )
                } else {
                    store.updateDelivery(
                        eventId = event.id,
                        status = "FAILED",
                        httpStatus = status,
                        error = responseText.ifBlank { "HTTP $status" }.take(500),
                        deliveredAt = null,
                    )
                }
            } catch (error: Exception) {
                store.updateDelivery(
                    eventId = event.id,
                    status = "FAILED",
                    httpStatus = null,
                    error = error.message?.take(500) ?: error.javaClass.simpleName,
                    deliveredAt = null,
                )
            } finally {
                connection?.disconnect()
                appContext.sendBroadcast(
                    Intent(ACTION_EVENTS_CHANGED).setPackage(appContext.packageName),
                )
            }
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun hmacSha256(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
