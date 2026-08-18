package kr.co.bitecompany.depositagent

object DepositNotificationParser {
    fun parse(
        packageName: String,
        title: String?,
        body: String,
        postedAt: Long,
        deviceId: String,
    ): DepositEvent? {
        return KbDepositParser.parse(packageName, title, body, postedAt, deviceId)
            ?: HanaDepositParser.parse(packageName, title, body, postedAt, deviceId)
            ?: NhDepositParser.parse(packageName, title, body, postedAt, deviceId)
    }
}
