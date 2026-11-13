package moe.efojug.bettersplashscreen.runtime

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import moe.efojug.bettersplashscreen.policy.IconChoice

data class SplashIdentity(
    val sessionId: Long,
    val packageName: String,
    val component: ComponentName,
    val taskId: Int,
    val displayId: Int,
    val userId: Int,
    val user: UserHandle,
    val launcherHints: List<ComponentName>,
) {
    fun logFields(): String =
        "session=$sessionId package=$packageName component=${component.flattenToShortString()} " + "task=$taskId display=$displayId user=$userId"
}

class SplashSession(
    val identity: SplashIdentity,
    val host: Context,
    val drawer: Any,
    val activity: ActivityInfo,
    val background: Int,
    val originalType: Int,
    var defaultIconSize: Int = 0,
) {
    var shellBuilder: Any? = null
    var shellBuildDepth = 0
    var icon: IconChoice<Drawable>? = null
    var vendorCandidate: Drawable? = null
    var vendorProbeDepth = 0
    var failed = false
    var modified = false
}

/** Contains no View/Drawable/Context references; safe as the value of a weak View-keyed map. */
data class SplashTag(val identity: SplashIdentity)
