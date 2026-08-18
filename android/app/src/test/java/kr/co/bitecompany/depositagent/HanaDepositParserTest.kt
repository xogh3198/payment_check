package kr.co.bitecompany.depositagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HanaDepositParserTest {
    @Test
    fun parsesHanaDepositWithAmountAfterDepositLabel() {
        val postedAt = Instant.parse("2026-08-18T05:31:00Z").toEpochMilli()
        val event = HanaDepositParser.parse(
            packageName = "com.kebhana.hanapush",
            title = "하나원큐 입출금 알림",
            body = """
                [하나은행] 08/18 14:31
                123-9100-****
                입금 30,000원
                이태호4821
                잔액 120,000원
            """.trimIndent(),
            postedAt = postedAt,
            deviceId = "device-1",
        )

        assertNotNull(event)
        assertEquals("HANA", event?.bank)
        assertEquals(30_000L, event?.amount)
        assertEquals("이태호4821", event?.depositorName)
        assertEquals("123-9100-****", event?.accountMasked)
        assertEquals("PARSED", event?.parseStatus)
    }

    @Test
    fun parsesHanaDepositWithNamedDepositorAndAmountBeforeDepositLabel() {
        val event = HanaDepositParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "하나은행",
            body = """
                2026.08.18 14:32
                123456**7890
                입금자: 김하나
                50,000원 입금
            """.trimIndent(),
            postedAt = System.currentTimeMillis(),
            deviceId = "device-1",
        )

        assertNotNull(event)
        assertEquals(50_000L, event?.amount)
        assertEquals("김하나", event?.depositorName)
        assertEquals("123456**7890", event?.accountMasked)
        assertEquals("PARSED", event?.parseStatus)
    }

    @Test
    fun ignoresNonHanaAndWithdrawalNotifications() {
        val now = System.currentTimeMillis()
        assertNull(HanaDepositParser.parse("messages", "택배", "배송 완료", now, "device-1"))
        assertNull(HanaDepositParser.parse("messages", "하나원큐", "10,000원 출금", now, "device-1"))
    }

    @Test
    fun commonParserRoutesHanaNotification() {
        val event = DepositNotificationParser.parse(
            packageName = "com.kebhana.hanapush",
            title = "하나원큐",
            body = "입금 1,000원\n테스트입금자",
            postedAt = System.currentTimeMillis(),
            deviceId = "device-1",
        )

        assertEquals("HANA", event?.bank)
    }

    @Test
    fun recognizesNewHanaOneQPackageWithoutBankNameInMessage() {
        val event = DepositNotificationParser.parse(
            packageName = "com.hanabank.oqf",
            title = "입출금 알림",
            body = "입금 10,000원\n테스트입금자",
            postedAt = System.currentTimeMillis(),
            deviceId = "device-1",
        )

        assertEquals("HANA", event?.bank)
        assertEquals(10_000L, event?.amount)
        assertEquals("테스트입금자", event?.depositorName)
    }
}
