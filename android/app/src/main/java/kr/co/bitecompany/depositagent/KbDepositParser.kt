package kr.co.bitecompany.depositagent

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object KbDepositParser {
    private val amountPattern = Regex("([0-9][0-9,]*)\\s*(?:원)?\\s*입금")
    private val accountPattern = Regex("[0-9]{3,}[*]{2,}[0-9]{2,}")
    private val datePattern = Regex("(?:\\[KB\\])?\\s*([0-9]{1,2})/([0-9]{1,2})\\s+([0-9]{1,2}):([0-9]{2})")
    private val phonePattern = Regex("[0-9]{2,4}-[0-9]{3,4}-[0-9]{4}")

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

        val looksLikeKb = rawText.contains("[KB]", ignoreCase = true) ||
            rawText.contains("KB국민", ignoreCase = true) ||
            rawText.contains("국민은행", ignoreCase = true)
        if (!looksLikeKb || !rawText.contains("입금")) return null

        val amount = amountPattern.find(rawText)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toLongOrNull()
        val accountMasked = accountPattern.find(rawText)?.value
        val transactionAt = parseTransactionAt(rawText, postedAt)
        val depositorName = findDepositorName(rawText)
        val parseStatus = if (amount != null && depositorName != null && transactionAt != null) {
            "parsed"
        } else {
            "partial"
        }
        val receivedAt = Instant.now().toString()

        return DepositEvent(
            id = sha256("$packageName|$postedAt|$rawText"),
            provider = "KB",
            paymentMethod = "bank_transfer",
            eventType = "deposit",
            packageName = packageName,
            notificationTitle = cleanTitle,
            rawText = rawText,
            postedAt = postedAt,
            receivedAt = receivedAt,
            transactionAt = transactionAt,
            accountMasked = accountMasked,
            payerName = depositorName,
            amount = amount,
            parseStatus = parseStatus,
        )
    }

    private fun findDepositorName(rawText: String): String? {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val amountIndex = lines.indexOfFirst { amountPattern.containsMatchIn(it) }
        if (amountIndex <= 0) return null

        return lines.subList(0, amountIndex)
            .asReversed()
            .firstOrNull { candidate ->
                !candidate.contains("[KB]", ignoreCase = true) &&
                    !candidate.contains("국민은행", ignoreCase = true) &&
                    !accountPattern.containsMatchIn(candidate) &&
                    !datePattern.containsMatchIn(candidate) &&
                    !phonePattern.containsMatchIn(candidate) &&
                    candidate.length in 1..20
            }
    }

    private fun parseTransactionAt(rawText: String, postedAt: Long): String? {
        val match = datePattern.find(rawText) ?: return Instant.ofEpochMilli(postedAt).toString()
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val hour = match.groupValues[3].toIntOrNull() ?: return null
        val minute = match.groupValues[4].toIntOrNull() ?: return null
        val zone = ZoneId.systemDefault()
        val postedDateTime = Instant.ofEpochMilli(postedAt).atZone(zone)

        return runCatching {
            var candidate = LocalDateTime.of(postedDateTime.year, month, day, hour, minute)
            if (candidate.isAfter(postedDateTime.toLocalDateTime().plusDays(1))) {
                candidate = candidate.minusYears(1)
            }
            candidate.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.getOrNull() ?: Instant.ofEpochMilli(postedAt).toString()
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
