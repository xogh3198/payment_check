package kr.co.bitecompany.depositagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class NhDepositParserTest {
    @Test
    fun parsesNhSmartBankingDeposit() {
        val postedAt = Instant.parse("2026-08-18T05:32:00Z").toEpochMilli()
        val event = NhDepositParser.parse(
            packageName = "nh.smart.banking",
            title = "NH스마트뱅킹 입출금 알림",
            body = """
                [NH농협] 08/18 14:32
                302-****-1234-**
                30,000원 입금
                이태호4821
                잔액 120,000원
            """.trimIndent(),
            postedAt = postedAt,
            deviceId = "device-1",
        )

        assertNotNull(event)
        assertEquals("NH", event?.provider)
        assertEquals(30_000L, event?.amount)
        assertEquals("이태호4821", event?.payerName)
        assertEquals("302-****-1234-**", event?.accountMasked)
        assertEquals("parsed", event?.parseStatus)
    }

    @Test
    fun parsesNhSmsWithExplicitDepositor() {
        val event = NhDepositParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "농협은행",
            body = """
                2026.08.18 14:33
                123456**7890
                입금자: 김농협
                입금 50,000원
            """.trimIndent(),
            postedAt = System.currentTimeMillis(),
            deviceId = "device-1",
        )

        assertNotNull(event)
        assertEquals(50_000L, event?.amount)
        assertEquals("김농협", event?.payerName)
        assertEquals("123456**7890", event?.accountMasked)
    }

    @Test
    fun recognizesAllOneAndCokPackagesWithoutBankName() {
        val now = System.currentTimeMillis()
        val allOneEvent = DepositNotificationParser.parse(
            packageName = "com.nonghyup.nhallonebank",
            title = "입출금 알림",
            body = "입금 10,000원\n올원테스트",
            postedAt = now,
            deviceId = "device-1",
        )
        val cokEvent = DepositNotificationParser.parse(
            packageName = "nh.smart.nhcok",
            title = "입출금 알림",
            body = "20,000원 입금\n콕테스트",
            postedAt = now + 1,
            deviceId = "device-1",
        )

        assertEquals("NH", allOneEvent?.provider)
        assertEquals("NH", cokEvent?.provider)
    }

    @Test
    fun ignoresNonNhAndWithdrawalNotifications() {
        val now = System.currentTimeMillis()
        assertNull(NhDepositParser.parse("messages", "택배", "배송 완료", now, "device-1"))
        assertNull(NhDepositParser.parse("nh.smart.banking", "입출금 알림", "10,000원 출금", now, "device-1"))
    }
}
