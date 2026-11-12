package moe.efojug.bettersplashscreen.status

import android.app.BroadcastOptions
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import moe.efojug.bettersplashscreen.BuildConfig
import java.util.UUID

class ModuleStatusRepository internal constructor(private val app: Application) {
    var framework by mutableStateOf("")
        private set
    var scoped by mutableStateOf(false)
        private set
    var hook by mutableStateOf<HookReport?>(null)
        private set
    var checking by mutableStateOf(true)
        private set
    private val main = Handler(Looper.getMainLooper())
    private var service: XposedService? = null
    private var pending: String? = null
    private var registered = false
    private var timeout: Runnable? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != StatusProtocol.REPLY) return
            val uid = try {
                app.packageManager.getApplicationInfo(StatusProtocol.SYSTEM_UI, 0).uid
            } catch (_: Exception) {
                return
            }
            if (!StatusProtocol.accepts(
                    sentFromUid,
                    uid,
                    intent.getStringExtra("nonce"),
                    pending,
                    intent.getIntExtra("version", -1),
                    BuildConfig.VERSION_CODE
                )
            ) return
            val stage = HookStage.entries.singleOrNull { it.name == intent.getStringExtra("stage") }
                ?: return
            timeout?.let(main::removeCallbacks)
            timeout = null
            pending = null
            checking = false
            hook = HookReport(
                stage,
                intent.getBooleanExtra("hit", false),
                intent.getStringExtra("reason").orEmpty().take(1500)
            )
        }
    }

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                main.post {
                    this@ModuleStatusRepository.service = service
                    try {
                        framework =
                            "${service.frameworkName} ${service.frameworkVersion} · API ${service.apiVersion}"
                        scoped = StatusProtocol.SYSTEM_UI in service.scope
                    } catch (_: Exception) {
                        framework = ""; scoped = false
                    }
                }
            }

            override fun onServiceDied(service: XposedService) {
                main.post {
                    if (this@ModuleStatusRepository.service === service) {
                        this@ModuleStatusRepository.service = null
                        framework = ""
                        scoped = false
                    }
                }
            }
        })
    }

    fun start() {
        if (registered) return
        app.registerReceiver(
            receiver, IntentFilter(StatusProtocol.REPLY), Context.RECEIVER_EXPORTED
        )
        registered = true
        refresh()
    }

    fun refresh() {
        if (!registered || pending != null) return
        checking = true
        service?.let {
            try {
                scoped = StatusProtocol.SYSTEM_UI in it.scope
            } catch (_: Exception) {
                framework = ""; scoped = false
            }
        }
        timeout?.let(main::removeCallbacks)
        val nonce = UUID.randomUUID().toString()
        pending = nonce
        timeout = Runnable {
            if (pending == nonce) {
                pending = null; timeout = null; hook = null; checking = false
            }
        }.also { main.postDelayed(it, 2000) }
        try {
            app.sendBroadcast(
                Intent(StatusProtocol.QUERY).setPackage(StatusProtocol.SYSTEM_UI)
                    .putExtra("nonce", nonce),
                null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            )
        } catch (_: Exception) {
            timeout?.let(main::removeCallbacks)
            timeout = null
            pending = null
            hook = null
            checking = false
        }
    }

    fun stop() {
        if (registered) app.unregisterReceiver(receiver)
        registered = false
        timeout?.let(main::removeCallbacks)
        timeout = null
        pending = null
        hook = null
        checking = true
    }

}
