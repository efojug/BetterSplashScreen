package moe.efojug.bettersplashscreen.entry

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import moe.efojug.bettersplashscreen.compat.BindingException
import moe.efojug.bettersplashscreen.compat.ShellBindings
import moe.efojug.bettersplashscreen.diagnostics.HookLog
import moe.efojug.bettersplashscreen.hook.ShellHookInstaller
import moe.efojug.bettersplashscreen.status.HookStatusReporter
import java.util.Collections
import java.util.IdentityHashMap
import java.lang.reflect.Method

class SplashModule : XposedModule() {
    private var targetProcess = false
    private lateinit var diagnostics: HookLog
    private val attempted = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
    private val installers = ArrayList<ShellHookInstaller>()
    private val installedMembers = HashSet<Method>()

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        diagnostics = HookLog(this)
        targetProcess = param.processName == "com.android.systemui"
        if (!targetProcess) return
        diagnostics.info(
            "event=module_loaded process=${param.processName} api=$apiVersion " + "framework=$frameworkName version=$frameworkVersion sdk=${Build.VERSION.SDK_INT} " + "fingerprint=${Build.FINGERPRINT} rom_validation=unverified"
        )
    }

    @Synchronized
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!targetProcess || param.packageName != "com.android.systemui") return
        synchronized(attempted) { if (!attempted.add(param.classLoader)) return }
        try {
            HookStatusReporter.start()
        } catch (e: Exception) {
            diagnostics.error("event=status_channel result=unavailable", e)
        }
        try {
            val bindings = ShellBindings.resolve(param.classLoader)
            if (bindings.coreMethods.any { it in installedMembers }) {
                diagnostics.info("event=install_group name=basic_splash result=skipped reason=member_already_owned")
                return
            }
            bindings.signatures().forEach {
                diagnostics.info("event=capability name=basic_splash status=resolved signature=$it")
            }
            ShellHookInstaller(this, bindings, diagnostics).also {
                if (it.install()) {
                    HookStatusReporter.installed()
                    installers.add(it)
                    installedMembers.addAll(bindings.coreMethods)
                    bindings.vendorIconProbe?.let(installedMembers::add)
                } else {
                    HookStatusReporter.failed("核心 Hook 安装失败，已停用本组修改。详见 BetterSplash 日志。")
                }
            }
        } catch (e: Exception) {
            HookStatusReporter.failed("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            val status = (e as? BindingException)?.status ?: "unavailable"
            diagnostics.error(
                "event=capability name=basic_splash status=$status " + "result=disabled fingerprint=${Build.FINGERPRINT}",
                e
            )
        } catch (e: LinkageError) {
            HookStatusReporter.failed("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            diagnostics.error(
                "event=capability name=basic_splash status=unavailable " + "result=disabled reason=class_linkage fingerprint=${Build.FINGERPRINT}",
                e
            )
        }
    }
}
