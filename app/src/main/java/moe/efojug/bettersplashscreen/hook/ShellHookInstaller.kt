package moe.efojug.bettersplashscreen.hook

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import moe.efojug.bettersplashscreen.R
import moe.efojug.bettersplashscreen.compat.ColorOsThemeAdapter
import moe.efojug.bettersplashscreen.compat.FieldEdits
import moe.efojug.bettersplashscreen.compat.ShellBindings
import moe.efojug.bettersplashscreen.diagnostics.HookLog
import moe.efojug.bettersplashscreen.policy.SplashPolicy
import moe.efojug.bettersplashscreen.status.HookStatusReporter
import moe.efojug.bettersplashscreen.runtime.CallScope
import moe.efojug.bettersplashscreen.runtime.SessionIconLoader
import moe.efojug.bettersplashscreen.runtime.SplashIdentity
import moe.efojug.bettersplashscreen.runtime.SplashSession
import moe.efojug.bettersplashscreen.runtime.SplashTag
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/** Installs one core group transactionally. No callback mutates anything before the group is ready. */
class ShellHookInstaller(
    private val module: XposedModule,
    private val bindings: ShellBindings,
    private val log: HookLog,
) {
    @Volatile
    private var enabled = false
    private val scope = CallScope<SplashSession>()
    private val ids = AtomicLong()
    private val views = Collections.synchronizedMap(WeakHashMap<View, SplashTag>())
    private val handles = LinkedHashMap<Method, XposedInterface.HookHandle>()
    private val icons = SessionIconLoader(bindings, ColorOsThemeAdapter(log), log)

    fun install(): Boolean {
        check(handles.isEmpty()) { "Installer already used" }
        try {
            add(bindings.makeContent, "content", ::makeContent)
            add(bindings.buildShell, "shell_builder", ::buildShell)
            add(bindings.buildFramework, "framework_builder", ::buildFramework)
            add(bindings.backgroundColor, "background_color") { chain ->
                // Preserve cache/supplier side effects; never catch and retry proceed().
                val result = chain.proceed()
                val session = scope.current
                if (session != null && !session.failed && sameActivity(chain.args[0], session)) {
                    session.background
                } else result
            }
            add(bindings.vendorBackground, "vendor_background") { chain ->
                val view = chain.args[0] as? View
                val tag = view?.let { views[it] }
                if (tag != null) {
                    log.once(
                        "preview_blocked",
                        "event=hook_hit capability=preview_background_blocked ${tag.identity.logFields()}"
                    )
                    null
                } else chain.proceed()
            }
            enabled = true
            log.info("event=install_group name=basic_splash result=installed runtime_hit=unverified")
        } catch (e: Exception) {
            enabled = false
            handles.values.toList().asReversed().forEach { handle ->
                try {
                    handle.unhook()
                } catch (cleanup: Exception) {
                    e.addSuppressed(cleanup)
                }
            }
            handles.clear()
            log.error(
                "event=install_group name=basic_splash result=disabled reason=installation_failed",
                e
            )
            return false
        }
        // Optional observation cannot disable the independently working core group.
        val probe = bindings.vendorIconProbe
        if (probe != null) {
            try {
                add(probe, "vendor_icon_probe", ::observeVendorIcon)
            } catch (e: Exception) {
                log.error("event=capability name=vendor_icon_probe status=not_installed", e)
            }
        } else {
            log.info(
                "event=capability name=vendor_icon_probe status=missing " + "expected=getIconExt(Context,ActivityInfo,int,boolean,boolean,boolean):Drawable"
            )
        }
        log.info("event=capability name=desktop_theme status=unverified reason=no_validated_firmware_profile")
        return true
    }

    private fun add(method: Method, id: String, interceptor: (XposedInterface.Chain) -> Any?) {
        check(method !in handles) { "Duplicate member ${method.toGenericString()}" }
        handles[method] = module.hook(method).setId("better_splash_$id")
            // Recoverable module failures are handled locally. Original exceptions must propagate unchanged.
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain -> if (enabled) interceptor(chain) else chain.proceed() }
        log.info("event=hook_install id=$id result=installed signature=${method.toGenericString()}")
    }

    private fun makeContent(chain: XposedInterface.Chain): Any? {
        val originalType = chain.args[2] as Int
        if (!SplashPolicy.isSplash(originalType)) return chain.proceed()
        val drawer = chain.thisObject ?: return chain.proceed()
        val session = try {
            createSession(drawer, chain.args[0] as Context, chain.args[1])
        } catch (e: Exception) {
            log.once(
                "identity_failure",
                "event=content_skip reason=identity_or_context_unavailable error=${e.javaClass.simpleName}"
            )
            null
        } ?: return chain.proceed()
        val args =
            chain.args.toTypedArray().apply { this[2] = SplashPolicy.restoreType(originalType) }
        return scope.with(session) {
            try {
                // This entry is already executing on Shell's content worker; no asynchronous chain storage.
                val result = chain.proceed(args)
                log.info(
                    "event=content_complete type=$originalType->${args[2]} modified=${session.modified} " + "failed=${session.failed} background=#${
                        Integer.toHexString(
                            session.background
                        )
                    } ${session.identity.logFields()}"
                )
                result
            } finally {
                session.vendorCandidate = null
                session.icon = null
                session.shellBuilder = null
            }
        }
    }

    private fun createSession(drawer: Any, appContext: Context, info: Any): SplashSession? {
        val flags = bindings.infoFields.getValue("startingWindowTypeParameter").getInt(info)
        if (flags and bindings.windowlessFlag != 0) return null
        val task = bindings.infoFields.getValue("taskInfo").get(info) ?: return null
        val activity = (bindings.infoFields.getValue("targetActivityInfo").get(info)
            ?: bindings.taskFields.getValue("topActivityInfo").get(task)) as? ActivityInfo
            ?: return null
        if (activity.packageName.isNullOrBlank() || activity.name.isNullOrBlank()) return null
        // Standard/undefined task only; do not decorate Home, Recents, Assistant or Dream windows.
        if (bindings.taskFields.getValue("topActivityType").getInt(task) !in 0..1) return null
        val attributes = appContext.obtainStyledAttributes(R.styleable.SplashWindowEligibility)
        val excluded = try {
            attributes.getBoolean(
                R.styleable.SplashWindowEligibility_android_windowIsTranslucent, false
            ) || attributes.getBoolean(
                R.styleable.SplashWindowEligibility_android_windowIsFloating, false
            )
        } finally {
            attributes.recycle()
        }
        if (excluded) return null
        val userId = bindings.taskFields.getValue("userId").getInt(task)
        if (activity.applicationInfo.uid / 100000 != userId) return null
        val component = ComponentName(activity.packageName, activity.name)
        val hints = listOfNotNull(
            component,
            bindings.taskFields.getValue("origActivity").get(task) as? ComponentName,
            bindings.taskFields.getValue("baseActivity").get(task) as? ComponentName,
        ).filter { it.packageName == activity.packageName }.distinct()
        val hostContext = bindings.drawerFields.getValue("mContext").get(drawer) as Context
        val host = hostContext.applicationContext ?: return null
        if (host.packageName != "com.android.systemui") return null
        val identity = SplashIdentity(
            ids.incrementAndGet(),
            activity.packageName,
            component,
            bindings.taskFields.getValue("taskId").getInt(task),
            bindings.taskFields.getValue("displayId").getInt(task),
            userId,
            UserHandle.getUserHandleForUid(activity.applicationInfo.uid),
            hints
        )
        return SplashSession(
            identity,
            host,
            drawer,
            activity,
            SplashPolicy.background(host.resources.configuration.uiMode)
        )
    }

    private fun buildShell(chain: XposedInterface.Chain): Any? {
        val session = scope.current ?: return chain.proceed()
        if (session.failed || session.shellBuildDepth != 0) return chain.proceed()
        val self = chain.thisObject ?: return chain.proceed()
        val fields = bindings.shellFields
        val edits = ArrayList<FieldEdits>()
        val prepared = try {
            run {
                if (fields.getValue("this\$0").get(self) !== session.drawer || !sameActivity(
                        fields.getValue("mActivityInfo").get(self), session
                    )
                ) return@run false
                val size = bindings.drawerFields.getValue("mIconSize").getInt(session.drawer)
                check(size > 0) { "System icon size is unavailable" }
                session.defaultIconSize = size
                val attrs = bindings.drawerFields.getValue("mTmpAttrs").get(session.drawer)
                    ?: error("Window attributes unavailable")
                val attrEdit = FieldEdits(attrs).also { edits.add(it) }
                // Write every content attribute we own; no reparse of app theme is needed for fixed opaque content.
                mapOf(
                    "mWindowBgResId" to 0,
                    "mWindowBgColor" to session.background,
                    "mIconBgColor" to 0,
                    "mSplashScreenIcon" to null,
                    "mBrandingImage" to null
                ).forEach { (name, value) ->
                    attrEdit.set(
                        bindings.attrsFields.getValue(name), value
                    )
                }
                val builderEdit = FieldEdits(self).also { edits.add(it) }
                mapOf(
                    "mSuggestType" to 1,
                    "mOverlayDrawable" to null,
                    "mThemeColor" to session.background,
                    "mForceBigIcon" to true,
                    "mIsSupportSplashScreenPreview" to false,
                    "mFinalIconSize" to size
                ).forEach { (name, value) -> builderEdit.set(fields.getValue(name), value) }
                true
            }
        } catch (e: Exception) {
            session.failed = true
            log.error("event=prepare_failed stage=shell_builder ${session.identity.logFields()}", e)
            restore(edits)
            false
        }
        if (!prepared) return chain.proceed()
        session.shellBuilder = self
        session.shellBuildDepth++
        return try {
            chain.proceed()
        } finally {
            session.shellBuildDepth--
            session.shellBuilder = null
            restore(edits)
        }
    }

    private fun buildFramework(chain: XposedInterface.Chain): Any? {
        val session = scope.current ?: return chain.proceed()
        if (session.failed || session.shellBuildDepth != 1 || session.shellBuilder == null) return chain.proceed()
        val self = chain.thisObject ?: return chain.proceed()
        val fields = bindings.frameworkFields
        val edit = FieldEdits(self)
        val prepared = try {
            run {
                // Never overwrite a builder restoring an application-side transferred splash/Surface.
                if (fields.getValue("mSurfacePackage")
                        .get(self) != null || fields.getValue("mParceledIconBitmap")
                        .get(self) != null
                ) return@run false
                val icon = icons.load(session)
                mapOf(
                    "mIconSize" to session.defaultIconSize,
                    "mBackgroundColor" to session.background,
                    "mIconDrawable" to icon.icon,
                    "mOverlayDrawable" to null,
                    "mIconBackground" to null,
                    "mBrandingDrawable" to null
                ).forEach { (name, value) -> edit.set(fields.getValue(name), value) }
                true
            }
        } catch (e: Exception) {
            session.failed = true
            log.error(
                "event=prepare_failed stage=framework_builder ${session.identity.logFields()}", e
            )
            restore(listOf(edit))
            false
        }
        if (!prepared) return chain.proceed()
        val result = try {
            chain.proceed()
        } finally {
            restore(listOf(edit))
        }
        // After failures must return the result already obtained, never call the original again.
        try {
            if (result is View && bindings.view.isInstance(result)) {
                views[result] = SplashTag(session.identity)
                session.modified = true
                HookStatusReporter.markHit()
                result.setBackgroundColor(session.background)
                val icon = bindings.iconView.invoke(result) as? View
                val params = icon?.layoutParams as? FrameLayout.LayoutParams
                if (params != null) {
                    params.gravity = Gravity.CENTER
                    icon.layoutParams = params
                }
                log.once(
                    "core_hit",
                    "event=hook_hit capability=basic_splash result=modified ${session.identity.logFields()}"
                )
            }
        } catch (e: Exception) {
            log.error("event=decoration_failed ${session.identity.logFields()}", e)
        }
        return result
    }

    private fun observeVendorIcon(chain: XposedInterface.Chain): Any? {
        val session = scope.current ?: return chain.proceed()
        if (!sameActivity(chain.args[1], session)) return chain.proceed()
        val outermost = session.vendorProbeDepth++ == 0
        return try {
            val result = chain.proceed()
            if (outermost && result is Drawable) {
                session.vendorCandidate = result
                log.once(
                    "vendor_probe_hit",
                    "event=hook_hit capability=vendor_icon_probe theme_verified=false ${session.identity.logFields()}"
                )
            }
            result
        } finally {
            session.vendorProbeDepth--
        }
    }

    private fun sameActivity(value: Any?, session: SplashSession): Boolean {
        val activity = value as? ActivityInfo ?: return false
        return activity.packageName == session.identity.packageName && activity.name == session.activity.name && activity.applicationInfo?.uid == session.activity.applicationInfo.uid
    }

    private fun restore(edits: List<FieldEdits>) {
        edits.asReversed().forEach {
            try {
                it.close()
            } catch (e: Exception) {
                log.error("event=field_restore_failed", e)
            }
        }
    }
}
