package kr.co.bitecompany.depositagent

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BeepayNotificationParser {
    private const val BEEPAY_PACKAGE = "com.bizplay.bizzeropay"
    private val amountPatterns = listOf(
        Regex("(?:결제금액|승인금액|이용금액|금액)\\s*[:：]?\\s*([0-9][0-9,]*)\\s*원"),
        Regex("(?<![0-9])([0-9][0-9,]*)\\s*원\\s*(?:결제|승인|이용)"),
    )
    private val payerPatterns = listOf(
        Regex("(?:결제자|구매자|고객명|입금자|보낸\\s*사람)\\s*[:：]?\\s*([^\\n|,/]+)"),
    )
    private val shortDatePattern = Regex(
        "(?<![0-9])([0-9]{1,2})[./-]([0-9]{1,2})\\s+([0-9]{1,2}):([0-9]{2})",
    )
    private val fullDatePattern = Regex(
        "(?<![0-9])(20[0-9]{2})[./-]([0-9]{1,2})[./-]([0-9]{1,2})\\s+([0-9]{1,2}):([0-9]{2})",
    )

    fun parse(
        packageName: String,
        title: String?,
        body: String,
        postedAt: Long,
        @Suppress("UNUSED_PARAMETER")
        deviceId: String,
    ): DepositEvent? {
        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
        val cleanBody = body.trim()
        val rawText = listOfNotNull(cleanTitle, cleanBody.takeIf { it.isNotBlank() })
            .joinToString("\n")
            .replace("\r\n", "\n")
            .trim()

        val isBeepay = packageName == BEEPAY_PACKAGE ||
            rawText.contains("비플페이", ignoreCase = true) ||
            rawText.contains("수산대전상품권") ||
            rawText.contains("수산할인상품권")
        if (!isBeepay) return null

        val eventType = resolveEventType(rawText) ?: return null
        val amount = amountPatterns.asSequence()
            .mapNotNull { it.find(rawText)?.groupValues?.getOrNull(1) }
            .mapNotNull { it.replace(",", "").toLongOrNull() }
            .firstOrNull()
        val payerName = payerPatterns.asSequence()
            .mapNotNull { it.find(rawText)?.groupValues?.getOrNull(1)?.trim() }
            .firstOrNull { it.length in 1..120 }
        val transactionAt = parseTransactionAt(rawText, postedAt)
        val parseStatus = if (amount != null && payerName != null) "parsed" else "partial"

        return DepositEvent(
            id = sha256("$packageName|$postedAt|$rawText"),
            provider = "BEEPAY",
            paymentMethod = "fishery_voucher",
            eventType = eventType,
            packageName = packageName,
            notificationTitle = cleanTitle,
            rawText = rawText,
            postedAt = postedAt,
            receivedAt = Instant.now().toString(),
            transactionAt = transactionAt,
            accountMasked = null,
            payerName = payerName,
            amount = amount,
            parseStatus = parseStatus,
        )
    }

    private fun resolveEventType(rawText: String): String? = when {
        rawText.contains("환불") -> "refund"
        rawText.contains("결제취소") || rawText.contains("승인취소") -> "cancel"
        rawText.contains("결제완료") ||
            rawText.contains("결제 완료") ||
            rawText.contains("결제승인") ||
            rawText.contains("결제 승인") ||
            rawText.contains("결제되었습니다") -> "payment_received"
        else -> null
    }

    private fun parseTransactionAt(rawText: String, postedAt: Long): String {
        val zone = ZoneId.systemDefault()
        val postedDateTime = Instant.ofEpochMilli(postedAt).atZone(zone)

        fullDatePattern.find(rawText)?.let { match ->
            runCatching {
                return LocalDateTime.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].toInt(),
                ).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            }
        }

        shortDatePattern.find(rawText)?.let { match ->
            runCatching {
                var candidate = LocalDateTime.of(
                    postedDateTime.year,
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                )
                if (candidate.isAfter(postedDateTime.toLocalDateTime().plusDays(1))) {
                    candidate = candidate.minusYears(1)
                }
                return candidate.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            }
        }

        return Instant.ofEpochMilli(postedAt).toString()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
