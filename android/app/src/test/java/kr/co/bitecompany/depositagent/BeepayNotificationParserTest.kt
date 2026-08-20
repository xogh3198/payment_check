package kr.co.bitecompany.depositagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BeepayNotificationParserTest {
    @Test
    fun parsesIncomingFisheryVoucherPayment() {
        val postedAt = Instant.parse("2026-08-20T05:33:00Z").toEpochMilli()
        val event = BeepayNotificationParser.parse(
            packageName = "com.bizplay.bizzeropay",
            title = "비플페이 결제 완료",
            body = """
                수산대전상품권 결제완료
                결제금액: 30,000원
                결제자: 이태호4821
                08/20 14:33
            """.trimIndent(),
            postedAt = postedAt,
            deviceId = "device-1",
        )

        assertNotNull(event)
        assertEquals("BEEPAY", event?.provider)
        assertEquals("fishery_voucher", event?.paymentMethod)
        assertEquals("payment_received", event?.eventType)
        assertEquals(30_000L, event?.amount)
        assertEquals("이태호4821", event?.payerName)
        assertEquals("parsed", event?.parseStatus)
    }

    @Test
    fun storesRefundForReviewInsteadOfTreatingItAsPayment() {
        val event = BeepayNotificationParser.parse(
            packageName = "com.bizplay.bizzeropay",
            title = "비플페이 환불",
            body = "수산대전상품권 환불\n금액: 30,000원\n결제자: 이태호",
            postedAt = System.currentTimeMillis(),
            deviceId = "device-1",
        )

        assertEquals("refund", event?.eventType)
    }

    @Test
    fun ignoresUnrelatedNotification() {
        assertNull(
            BeepayNotificationParser.parse(
                packageName = "messages",
                title = "배송",
                body = "택배가 도착했습니다.",
                postedAt = System.currentTimeMillis(),
                deviceId = "device-1",
            ),
        )
    }
}
