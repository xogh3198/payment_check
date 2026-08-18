package kr.co.bitecompany.depositagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class KbDepositParserTest {
    @Test
    fun parsesKbDepositNotification() {
        val postedAt = Instant.parse("2026-08-18T05:30:00Z").toEpochMilli()
        val event = KbDepositParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "메시지",
            body = """
                [KB]8/18 14:30
                498125****8895
                이태호4821
                30,000 입금
                1644-9999
            """.trimIndent(),
            postedAt = postedAt,
            deviceId = "device-1",
        )

        assertNotNull(event)
        assertEquals(30_000L, event?.amount)
        assertEquals("이태호4821", event?.depositorName)
        assertEquals("498125****8895", event?.accountMasked)
        assertEquals("PARSED", event?.parseStatus)
        assertEquals(false, event?.toWebhookJson()?.getBoolean("isAuthoritative"))
    }

    @Test
    fun ignoresNonKbAndWithdrawalNotifications() {
        val now = System.currentTimeMillis()
        assertNull(KbDepositParser.parse("messages", "택배", "배송 완료", now, "device-1"))
        assertNull(KbDepositParser.parse("messages", "KB", "10,000 출금", now, "device-1"))
    }
}
