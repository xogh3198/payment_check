package kr.co.bitecompany.depositagent

import org.json.JSONObject

data class DepositEvent(
    val id: String,
    val provider: String,
    val paymentMethod: String,
    val eventType: String,
    val packageName: String,
    val notificationTitle: String?,
    val rawText: String,
    val postedAt: Long,
    val receivedAt: String,
    val transactionAt: String?,
    val accountMasked: String?,
    val payerName: String?,
    val amount: Long?,
    val parseStatus: String,
    val deliveryStatus: String = "PENDING",
    val deliveryAttempts: Int = 0,
    val lastHttpStatus: Int? = null,
    val lastError: String? = null,
    val deliveredAt: String? = null,
) {
    fun toWebhookJson(): JSONObject = JSONObject().apply {
        put("eventId", id)
        put("provider", provider)
        put("paymentMethod", paymentMethod)
        put("eventType", eventType)
        putNullable("amount", amount)
        put("currency", "KRW")
        putNullable("transactionAt", transactionAt)
        putNullable("payerName", payerName)
        putNullable("accountMasked", accountMasked)
        put("packageName", packageName)
        putNullable("notificationTitle", notificationTitle)
        put("rawText", rawText)
        put("postedAt", postedAt)
        put("parseStatus", parseStatus)
    }

    fun toStorageJson(): JSONObject = toWebhookJson().apply {
        put("receivedAt", receivedAt)
        put("deliveryStatus", deliveryStatus)
        put("deliveryAttempts", deliveryAttempts)
        putNullable("lastHttpStatus", lastHttpStatus)
        putNullable("lastError", lastError)
        putNullable("deliveredAt", deliveredAt)
    }

    companion object {
        fun fromStorageJson(json: JSONObject): DepositEvent = DepositEvent(
            id = json.getString("eventId"),
            provider = json.optString("provider", json.optString("bank", "KB")),
            paymentMethod = json.optString("paymentMethod").ifBlank {
                if (json.optString("provider", json.optString("bank")) == "BEEPAY") {
                    "fishery_voucher"
                } else {
                    "bank_transfer"
                }
            },
            eventType = json.optString("eventType", "deposit").lowercase(),
            packageName = json.optString("packageName"),
            notificationTitle = json.optNullableString("notificationTitle"),
            rawText = json.optString("rawText"),
            postedAt = json.optLong("postedAt"),
            receivedAt = json.optString("receivedAt"),
            transactionAt = json.optNullableString("transactionAt"),
            accountMasked = json.optNullableString("accountMasked"),
            payerName = json.optNullableString("payerName")
                ?: json.optNullableString("depositorName"),
            amount = if (json.isNull("amount")) null else json.optLong("amount"),
            parseStatus = json.optString("parseStatus", "partial").lowercase(),
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
