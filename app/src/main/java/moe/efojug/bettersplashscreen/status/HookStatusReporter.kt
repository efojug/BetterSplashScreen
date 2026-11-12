package moe.efojug.bettersplashscreen.status

import android.annotation.SuppressLint
import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import moe.efojug.bettersplashscreen.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Process-local state. Replies are fresh challenges, never persisted success flags. */
object HookStatusReporter {
    private val report = AtomicReference(HookReport())
    private val started = AtomicBoolean()
    fun installed() {
        report.updateAndGet { it.copy(stage = HookStage.INSTALLED) }
    }

    fun failed(reason: String) {
        report.set(HookReport(HookStage.FAILED, reason = reason.take(1500)))
    }

    fun markHit() {
        report.updateAndGet { it.copy(hit = true) }
    }

    @SuppressLint("PrivateApi") // SystemUI Application is not available at module construction time.
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val main = Handler(Looper.getMainLooper())
        var attempts = 0
        val register = object : Runnable {
            @SuppressLint("DiscouragedPrivateApi")
            override fun run() {
                try {
                    val context = Class.forName("android.app.ActivityThread")
                        .getDeclaredMethod("currentApplication").invoke(null) as? Context
                    if (context == null) {
                        if (++attempts < 40) main.postDelayed(this, 250)
                        return
                    }
                    val host = context.applicationContext
                    if (host.packageName != StatusProtocol.SYSTEM_UI) return
                    host.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (intent.action != StatusProtocol.QUERY) return
                            try {
                                val moduleUid = context.packageManager.getApplicationInfo(
                                    BuildConfig.APPLICATION_ID, 0
                                ).uid
                                if (sentFromUid != moduleUid) return
                                val nonce =
                                    intent.getStringExtra("nonce")?.takeIf { it.length == 36 }
                                        ?: return
                                val snapshot = report.get()
                                context.sendBroadcast(
                                    Intent(StatusProtocol.REPLY).setPackage(BuildConfig.APPLICATION_ID)
                                        .putExtra("nonce", nonce)
                                        .putExtra("version", BuildConfig.VERSION_CODE)
                                        .putExtra("stage", snapshot.stage.name)
                                        .putExtra("hit", snapshot.hit)
                                        .putExtra("reason", snapshot.reason),
                                    null,
                                    BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
                                        .toBundle()
                                )
                            } catch (e: Exception) {
                                Log.w("BetterSplash", "Hook status reply failed", e)
                            }
                        }
                    }, IntentFilter(StatusProtocol.QUERY), Context.RECEIVER_EXPORTED)
                } catch (e: Exception) {
                    Log.w("BetterSplash", "Hook status channel unavailable", e)
                }
            }
        }
        main.post(register)
    }
}
