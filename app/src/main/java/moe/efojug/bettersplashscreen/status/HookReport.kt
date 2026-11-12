package moe.efojug.bettersplashscreen.status

enum class HookStage { INSTALLING, INSTALLED, FAILED }

data class HookReport(
    val stage: HookStage = HookStage.INSTALLING,
    val hit: Boolean = false,
    val reason: String = "",
)

object StatusProtocol {
    const val QUERY = "moe.efojug.bettersplashscreen.QUERY_HOOK_STATUS"
    const val REPLY = "moe.efojug.bettersplashscreen.HOOK_STATUS"
    const val SYSTEM_UI = "com.android.systemui"

    fun accepts(
        sender: Int,
        expectedSender: Int,
        nonce: String?,
        pending: String?,
        version: Int,
        expectedVersion: Int
    ): Boolean =
        expectedSender >= 0 && sender == expectedSender && !pending.isNullOrEmpty() && nonce == pending && version == expectedVersion
}
