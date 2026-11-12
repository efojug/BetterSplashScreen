package moe.efojug.bettersplashscreen.runtime

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import moe.efojug.bettersplashscreen.compat.ColorOsThemeAdapter
import moe.efojug.bettersplashscreen.compat.ShellBindings
import moe.efojug.bettersplashscreen.diagnostics.HookLog
import moe.efojug.bettersplashscreen.policy.IconChoice
import moe.efojug.bettersplashscreen.policy.selectIcon

/** No process-wide icon cache: theme, package and configuration changes are visible next launch. */
class SessionIconLoader(
    private val bindings: ShellBindings,
    private val theme: ColorOsThemeAdapter,
    private val log: HookLog,
) {
    fun load(session: SplashSession): IconChoice<Drawable> {
        session.icon?.let { return it }
        val identity = session.identity
        var userContext: Context? = null
        fun context(): Context {
            userContext?.let { return it }
            val loaded = if (identity.user == Process.myUserHandle()) {
                session.host.createPackageContext(identity.packageName, Context.CONTEXT_RESTRICTED)
            } else {
                bindings.packageContextAsUser.invoke(
                    session.host, identity.packageName, Context.CONTEXT_RESTRICTED, identity.user
                ) as Context
            }
            return loaded.also { userContext = it }
        }

        val result = selectIcon(
            theme = { theme.resolve(session) },
            launcher = {
                val launcher = session.host.getSystemService(LauncherApps::class.java)
                    ?: error("LauncherApps unavailable")
                val activities = launcher.getActivityList(identity.packageName, identity.user)
                    .filter { UserId.ofUid(it.applicationInfo.uid) == identity.userId }
                val exact = identity.launcherHints.firstNotNullOfOrNull { hint ->
                    activities.singleOrNull { it.componentName == hint }
                }
                val selected: LauncherActivityInfo? = exact ?: activities.singleOrNull()
                ?: context().packageManager.getLaunchIntentForPackage(identity.packageName)?.component?.let { hint ->
                    activities.singleOrNull { it.componentName == hint }
                }
                if (selected == null) {
                    log.info(
                        "event=icon_fallback source=launcher_component reason=no_unique_launcher " + "count=${activities.size} ${identity.logFields()}"
                    )
                    null
                } else {
                    selected.getIcon(session.host.resources.displayMetrics.densityDpi)
                }
            },
            application = { context().packageManager.getApplicationIcon(identity.packageName) },
            default = { session.host.packageManager.defaultActivityIcon },
            onFailure = { source, exception ->
                log.error("event=icon_fallback source=$source ${identity.logFields()}", exception)
            },
        )
        val owned =
            result.icon.constantState?.newDrawable(session.host.resources)?.mutate() ?: result.icon
        return result.copy(icon = owned).also {
            session.icon = it
            log.info(
                "event=icon_selected source=${it.source} theme_verified=" + "${it.source == moe.efojug.bettersplashscreen.policy.IconSource.VERIFIED_COLOROS_THEME} ${identity.logFields()}"
            )
        }
    }
}

internal object UserId {
    // Android's stable PER_USER_RANGE; getUserHandleForUid supplies the actual UserHandle for API calls.
    fun ofUid(uid: Int): Int = uid / 100000
}
