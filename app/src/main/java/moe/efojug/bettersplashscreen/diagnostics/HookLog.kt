package moe.efojug.bettersplashscreen.diagnostics

import android.util.Log
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

class HookLog(private val module: XposedModule) {
    private val once = ConcurrentHashMap.newKeySet<String>()

    fun info(message: String) = module.log(Log.INFO, "BetterSplash", message)
    fun error(message: String, error: Throwable) =
        module.log(Log.WARN, "BetterSplash", message, error)

    fun once(key: String, message: String) {
        if (once.add(key)) info(message)
    }
}
