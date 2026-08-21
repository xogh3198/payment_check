package kr.co.bitecompany.depositagent

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class AgentEnrollment(
    val agentId: String,
    val agentSecret: String,
    val eventPath: String,
    val heartbeatPath: String,
)

data class AgentHttpResult(
    val status: Int,
    val responseBody: String,
) {
    val successful: Boolean get() = status in 200..299
    val retryable: Boolean get() = status == 408 || status == 425 || status == 429 || status >= 500
}

class AgentApiException(message: String, val status: Int? = null) : RuntimeException(message)

object AgentApiClient {
    private val secureRandom = SecureRandom()

    fun enroll(
        serverBaseUrl: String,
        enrollmentToken: String,
        installationId: String,
        appVersion: String,
    ): AgentEnrollment {
        val payload = JSONObject().apply {
            put("enrollmentToken", enrollmentToken)
            put("installationId", installationId)
            put("appVersion", appVersion)
        }.toString()
        val result = post(
            url = "${AgentPreferences.normalizeBaseUrl(serverBaseUrl)}/api/v1/payment-agents/enroll",
            payload = payload,
            headers = emptyMap(),
        )
        if (!result.successful) {
            throw AgentApiException(errorMessage(result), result.status)
        }

        return runCatching {
            val json = JSONObject(result.responseBody)
            AgentEnrollment(
                agentId = json.getString("agentId"),
                agentSecret = json.getString("agentSecret"),
                eventPath = json.getString("eventPath"),
                heartbeatPath = json.getString("heartbeatPath"),
            )
        }.getOrElse {
            throw AgentApiException("Agent 등록 응답 형식이 올바르지 않습니다.", result.status)
        }
    }

    fun sendEvent(preferences: AgentPreferences, event: DepositEvent): AgentHttpResult {
        return signedPost(preferences, preferences.eventPath, event.toWebhookJson().toString())
    }

    fun sendHeartbeat(
        preferences: AgentPreferences,
        notificationAccessEnabled: Boolean,
        batteryOptimizationIgnored: Boolean,
        appVersion: String,
    ): AgentHttpResult {
        val payload = JSONObject().apply {
            put("notificationAccessEnabled", notificationAccessEnabled)
            put("batteryOptimizationIgnored", batteryOptimizationIgnored)
            put("appVersion", appVersion)
        }.toString()
        return signedPost(preferences, preferences.heartbeatPath, payload)
    }

    fun signature(secret: String, timestamp: String, nonce: String, rawBody: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$timestamp.$nonce.$rawBody".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun signedPost(
        preferences: AgentPreferences,
        path: String,
        payload: String,
    ): AgentHttpResult {
        val agentId = preferences.agentId
            ?: return AgentHttpResult(401, "Agent가 등록되지 않았습니다.")
        val agentSecret = preferences.agentSecret
            ?: return AgentHttpResult(401, "Agent가 등록되지 않았습니다.")
        val timestamp = Instant.now().epochSecond.toString()
        val nonceBytes = ByteArray(24).also(secureRandom::nextBytes)
        val nonce = Base64.encodeToString(
            nonceBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return post(
            url = preferences.endpoint(path),
            payload = payload,
            headers = mapOf(
                "X-Deposit-Agent-Id" to agentId,
                "X-Deposit-Agent-Timestamp" to timestamp,
                "X-Deposit-Agent-Nonce" to nonce,
                "X-Deposit-Agent-Signature" to signature(agentSecret, timestamp, nonce, payload),
            ),
        )
    }

    private fun post(
        url: String,
        payload: String,
        headers: Map<String, String>,
    ): AgentHttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            AgentHttpResult(status, readResponse(connection))
        } catch (error: Exception) {
            throw AgentApiException(
                error.message?.take(300) ?: "서버 연결에 실패했습니다.",
            )
        } finally {
            connection?.disconnect()
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

    private fun errorMessage(result: AgentHttpResult): String {
        val parsed = runCatching {
            val value = JSONObject(result.responseBody).opt("message")
            when (value) {
                is String -> value
                else -> value?.toString()
            }
        }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() }
            ?: "Agent 등록에 실패했습니다. (HTTP ${result.status})"
    }
}
