package kr.co.bitecompany.depositagent

import org.json.JSONObject

data class DepositEvent(
    val id: String,
    val deviceId: String,
    val source: String,
    val bank: String,
    val packageName: String,
    val notificationTitle: String?,
    val rawText: String,
    val postedAt: Long,
    val receivedAt: String,
    val transactionAt: String?,
    val accountMasked: String?,
    val depositorName: String?,
    val amount: Long?,
    val direction: String,
    val parseStatus: String,
    val deliveryStatus: String = "PENDING",
    val deliveryAttempts: Int = 0,
    val lastHttpStatus: Int? = null,
    val lastError: String? = null,
    val deliveredAt: String? = null,
) {
    fun toWebhookJson(): JSONObject = JSONObject().apply {
        put("eventId", id)
        put("deviceId", deviceId)
        put("source", source)
        put("bank", bank)
        put("packageName", packageName)
        putNullable("notificationTitle", notificationTitle)
        put("rawText", rawText)
        put("postedAt", postedAt)
        put("receivedAt", receivedAt)
        putNullable("transactionAt", transactionAt)
        putNullable("accountMasked", accountMasked)
        putNullable("depositorName", depositorName)
        putNullable("amount", amount)
        put("currency", "KRW")
        put("direction", direction)
        put("parseStatus", parseStatus)
        put("isAuthoritative", false)
    }

    fun toStorageJson(): JSONObject = toWebhookJson().apply {
        put("deliveryStatus", deliveryStatus)
        put("deliveryAttempts", deliveryAttempts)
        putNullable("lastHttpStatus", lastHttpStatus)
        putNullable("lastError", lastError)
        putNullable("deliveredAt", deliveredAt)
    }

    companion object {
        fun fromStorageJson(json: JSONObject): DepositEvent = DepositEvent(
            id = json.getString("eventId"),
            deviceId = json.getString("deviceId"),
            source = json.optString("source", "android_notification"),
            bank = json.optString("bank", "KB"),
            packageName = json.optString("packageName"),
            notificationTitle = json.optNullableString("notificationTitle"),
            rawText = json.optString("rawText"),
            postedAt = json.optLong("postedAt"),
            receivedAt = json.optString("receivedAt"),
            transactionAt = json.optNullableString("transactionAt"),
            accountMasked = json.optNullableString("accountMasked"),
            depositorName = json.optNullableString("depositorName"),
            amount = if (json.isNull("amount")) null else json.optLong("amount"),
            direction = json.optString("direction", "DEPOSIT"),
            parseStatus = json.optString("parseStatus", "PARTIAL"),
            deliveryStatus = json.optString("deliveryStatus", "PENDING"),
            deliveryAttempts = json.optInt("deliveryAttempts", 0),
            lastHttpStatus = if (json.isNull("lastHttpStatus")) null else json.optInt("lastHttpStatus"),
            lastError = json.optNullableString("lastError"),
            deliveredAt = json.optNullableString("deliveredAt"),
        )
    }
}
private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
