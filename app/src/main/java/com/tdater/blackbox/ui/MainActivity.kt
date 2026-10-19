package com.tdater.blackbox.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import com.tdater.blackbox.system.QuickCapture
import com.tdater.blackbox.system.Reminders
import com.tdater.blackbox.ui.components.Lingo
import com.tdater.blackbox.ui.components.LocalLingo
import com.tdater.blackbox.ui.theme.LocalPalette
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.tdater.blackbox.ui.components.Backdrop
import com.tdater.blackbox.ui.theme.BlackboxTheme

/** Content handed to BLACKBOX from another app's share sheet, waiting for a project choice. */
data class PendingShare(val uris: List<Uri>, val text: String?, val subject: String?)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            BlackboxTheme(vm.theme) {
                val dark = LocalPalette.current.dark
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    )
                    onDispose { }
                }
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(vm.message) {
                    vm.message?.let { snack.showSnackbar(it); vm.message = null }
                }
                CompositionLocalProvider(LocalLingo provides Lingo.of(vm.wording)) {
                    Backdrop {
                        Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                BlackboxNav(vm)
                                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (vm.settings.hideInRecents) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.backupIfDue()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(QuickCapture.EXTRA_MODE)?.let { vm.pendingQuick = it; return }
        intent.getStringExtra(Reminders.EXTRA_PID)?.let { vm.pendingOpen = it to intent.getStringExtra(Reminders.EXTRA_IID); return }
        handleShare(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
            else -> return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (uris.isEmpty() && text.isNullOrBlank()) return
        vm.pendingShare = PendingShare(uris, text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }
}
