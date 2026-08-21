package kr.co.bitecompany.depositagent

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HanaDepositParser {
    private val hanaPackageNames = setOf(
        "com.hanabank.oqf",
        "com.kebhana.hanapush",
    )
    private val amountPatterns = listOf(
        Regex("입금(?:액)?(?![가-힣A-Za-z0-9])[ \\t]*[:：]?[ \\t]*([0-9][0-9,]*)[ \\t]*(?:원)?"),
        Regex("(?<![0-9,*])([0-9][0-9,]*)[ \\t]*(?:원)?[ \\t]*입금(?![가-힣A-Za-z0-9])"),
    )
    private val accountPattern = Regex(
        "(?<![0-9])(?:[0-9]{2,6}[- ]?)*[0-9]{0,6}[*]{2,}(?:[- ]?[0-9*]{2,8})*(?![0-9])",
    )
    private val shortDatePattern = Regex(
        "(?<![0-9])([0-9]{1,2})[./-]([0-9]{1,2})\\s+([0-9]{1,2}):([0-9]{2})",
    )
    private val fullDatePattern = Regex(
        "(?<![0-9])(20[0-9]{2})[./-]([0-9]{1,2})[./-]([0-9]{1,2})\\s+([0-9]{1,2}):([0-9]{2})",
    )
    private val phonePattern = Regex("[0-9]{2,4}-[0-9]{3,4}-[0-9]{4}")
    private val explicitDepositorPattern = Regex(
        "(?:입금자|보낸\\s*분|보낸\\s*사람|의뢰인|적요)\\s*[:：]?\\s*([^\\n|,/]+)",
    )
    private val moneyPattern = Regex("[0-9][0-9,]*\\s*원")

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

        if (!looksLikeHana(packageName, rawText) || !rawText.contains("입금")) return null

        val amount = findAmount(rawText)
        val accountMasked = accountPattern.find(rawText)?.value
        val transactionAt = parseTransactionAt(rawText, postedAt)
        val depositorName = findDepositorName(rawText)
        val parseStatus = if (amount != null && depositorName != null && transactionAt != null) {
            "parsed"
        } else {
            "partial"
        }

        return DepositEvent(
            id = sha256("$packageName|$postedAt|$rawText"),
            provider = "HANA",
            paymentMethod = "bank_transfer",
            eventType = "deposit",
            packageName = packageName,
            notificationTitle = cleanTitle,
            rawText = rawText,
            postedAt = postedAt,
            receivedAt = Instant.now().toString(),
            transactionAt = transactionAt,
            accountMasked = accountMasked,
            payerName = depositorName,
            amount = amount,
            parseStatus = parseStatus,
        )
    }

    private fun looksLikeHana(packageName: String, rawText: String): Boolean {
        return packageName in hanaPackageNames || containsHanaMarker(rawText)
    }

    private fun containsHanaMarker(rawText: String): Boolean {
        return rawText.contains("[하나은행]", ignoreCase = true) ||
            rawText.contains("[하나]", ignoreCase = true) ||
            rawText.contains("하나은행", ignoreCase = true) ||
            rawText.contains("하나원큐", ignoreCase = true) ||
            rawText.contains("KEB하나", ignoreCase = true)
    }

    private fun findAmount(rawText: String): Long? {
        return amountPatterns.asSequence()
            .mapNotNull { pattern -> pattern.find(rawText)?.groupValues?.getOrNull(1) }
            .mapNotNull { value -> value.replace(",", "").toLongOrNull() }
            .firstOrNull()
    }

    private fun findDepositorName(rawText: String): String? {
        explicitDepositorPattern.find(rawText)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { isDepositorCandidate(it, allowBankMarker = true) }
            ?.let { return it }

        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val amountIndex = lines.indexOfFirst { line ->
            amountPatterns.any { pattern -> pattern.containsMatchIn(line) }
        }
        if (amountIndex < 0) return null

        val nearbyIndexes = listOf(
            amountIndex + 1,
            amountIndex - 1,
            amountIndex + 2,
            amountIndex - 2,
        )
        return nearbyIndexes.asSequence()
            .filter { it in lines.indices }
            .map { lines[it] }
            .firstOrNull(::isDepositorCandidate)
    }

    private fun isDepositorCandidate(value: String, allowBankMarker: Boolean = false): Boolean {
        if (value.length !in 1..30) return false
        if (!allowBankMarker && containsHanaMarker(value)) return false
        if (value.matches(Regex("^(?:입금|출금|잔액)(?:\\s|[:：]|[0-9]).*"))) return false
        if (value.contains("계좌") || value.contains("거래") || value.contains("알림")) return false
        if (accountPattern.containsMatchIn(value)) return false
        if (shortDatePattern.containsMatchIn(value) || fullDatePattern.containsMatchIn(value)) return false
        if (phonePattern.containsMatchIn(value) || moneyPattern.containsMatchIn(value)) return false
        if (value.all { it.isDigit() || it == '-' || it == '*' || it.isWhitespace() }) return false
        return true
    }

    private fun parseTransactionAt(rawText: String, postedAt: Long): String? {
        val zone = ZoneId.systemDefault()
        val postedDateTime = Instant.ofEpochMilli(postedAt).atZone(zone)

        val fullDate = fullDatePattern.find(rawText)
        if (fullDate != null) {
            return formatDateTime(
                year = fullDate.groupValues[1].toIntOrNull(),
                month = fullDate.groupValues[2].toIntOrNull(),
                day = fullDate.groupValues[3].toIntOrNull(),
                hour = fullDate.groupValues[4].toIntOrNull(),
                minute = fullDate.groupValues[5].toIntOrNull(),
                zone = zone,
            )
        }

        val shortDate = shortDatePattern.find(rawText)
            ?: return Instant.ofEpochMilli(postedAt).toString()
        val month = shortDate.groupValues[1].toIntOrNull() ?: return null
        val day = shortDate.groupValues[2].toIntOrNull() ?: return null
        val hour = shortDate.groupValues[3].toIntOrNull() ?: return null
        val minute = shortDate.groupValues[4].toIntOrNull() ?: return null

        return runCatching {
            var candidate = LocalDateTime.of(postedDateTime.year, month, day, hour, minute)
            if (candidate.isAfter(postedDateTime.toLocalDateTime().plusDays(1))) {
                candidate = candidate.minusYears(1)
            }
            candidate.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.getOrNull() ?: Instant.ofEpochMilli(postedAt).toString()
    }

    private fun formatDateTime(
        year: Int?,
        month: Int?,
        day: Int?,
        hour: Int?,
        minute: Int?,
        zone: ZoneId,
    ): String? {
        if (year == null || month == null || day == null || hour == null || minute == null) return null
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(zone)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.getOrNull()
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
