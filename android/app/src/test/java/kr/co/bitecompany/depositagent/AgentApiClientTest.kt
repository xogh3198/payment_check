package kr.co.bitecompany.depositagent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentApiClientTest {
    @Test
    fun signsTimestampNonceAndExactRawBody() {
        assertEquals(
            "16bd8c6d3d4ec8e1e3cff18b56883b9bf13975a01c15539111baf247fdecd989",
            AgentApiClient.signature(
                secret = "agent-secret",
                timestamp = "1787200000",
                nonce = "0123456789abcdef",
                rawBody = "{\"eventId\":\"event-1\"}",
            ),
        )
    }
}
