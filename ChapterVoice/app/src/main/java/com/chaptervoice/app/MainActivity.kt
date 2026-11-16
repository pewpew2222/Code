package com.chaptervoice.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.chaptervoice.app.browser.TabManager
import com.chaptervoice.app.ui.AppTheme
import com.chaptervoice.app.ui.ReaderScreen

class MainActivity : ComponentActivity() {

    /** A URL shared to the app from another app (e.g. a browser). */
    private lateinit var tabs: TabManager

    private val incomingUrl = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdBlocker.start(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleIntent(intent)

        val settings = AppSettings(this)
        tabs = TabManager(this, settings)
        setContent {
            AppTheme {
                ReaderScreen(
                    settings = settings,
                    tabs = tabs,
                    incomingUrl = incomingUrl.value,
                    onIncomingUrlHandled = { incomingUrl.value = null },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        tabs.save()
    }

    override fun onDestroy() {
        tabs.destroyAll()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val matcher = Patterns.WEB_URL.matcher(text)
        if (matcher.find()) incomingUrl.value = matcher.group()
    }
}
