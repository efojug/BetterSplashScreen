package moe.efojug.bettersplashscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import moe.efojug.bettersplashscreen.status.HookStage
import moe.efojug.bettersplashscreen.status.ModuleStatusRepository
import moe.efojug.bettersplashscreen.ui.theme.BetterSplashScreenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val status = (application as BetterSplashApplication).moduleStatus
        setContent {
            BetterSplashScreenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ModuleOverview(status, lifecycle, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun ModuleOverview(status: ModuleStatusRepository, lifecycle: Lifecycle, modifier: Modifier = Modifier) {
    LaunchedEffect(status, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            status.start()
            try {
                awaitCancellation()
            } finally { status.stop() }
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        val connected = status.framework.isNotEmpty()
        StatusCard(stringResource(R.string.module_status_title),
            stringResource(if (!connected) R.string.module_disconnected else if (!status.scoped)
                R.string.module_scope_missing else R.string.module_enabled),
            if (connected) status.framework else stringResource(R.string.module_disconnected_detail),
            connected && status.scoped)
        val hook = status.hook
        val title = when {
            hook?.stage == HookStage.FAILED -> R.string.hook_failed
            hook?.stage == HookStage.INSTALLED && hook.hit -> R.string.hook_hit
            hook?.stage == HookStage.INSTALLED -> R.string.hook_installed
            hook?.stage == HookStage.INSTALLING || status.checking -> R.string.hook_checking
            else -> R.string.hook_unknown
        }
        val detail = when {
            hook?.stage == HookStage.FAILED -> hook.reason
            hook?.stage == HookStage.INSTALLED && hook.hit -> stringResource(R.string.hook_hit_detail)
            hook?.stage == HookStage.INSTALLED -> stringResource(R.string.hook_installed_detail)
            status.checking -> stringResource(R.string.hook_checking_detail)
            else -> stringResource(R.string.hook_unknown_detail)
        }
        StatusCard(stringResource(R.string.hook_status_title), stringResource(title), detail,
            hook?.stage == HookStage.INSTALLED)
        TextButton(onClick = status::refresh, enabled = !status.checking) {
            Text(stringResource(if (status.checking) R.string.refreshing_status else R.string.refresh_status))
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, detail: String, success: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall,
                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
