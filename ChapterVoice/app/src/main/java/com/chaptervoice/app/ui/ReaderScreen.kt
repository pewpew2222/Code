package com.chaptervoice.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.core.NextMode
import com.chaptervoice.app.service.ReaderService
import com.chaptervoice.app.service.ReaderState
import com.chaptervoice.app.service.ReaderStatus
import com.chaptervoice.app.service.ReaderUiState
import org.json.JSONTokener
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    settings: AppSettings,
    incomingUrl: String?,
    onIncomingUrlHandled: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by ReaderState.state.collectAsStateWithLifecycle()

    var addressText by remember { mutableStateOf(settings.lastUrl) }
    var pageProgress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val webView = remember {
        createWebView(
            context,
            onPageStarted = { url -> addressText = url },
            onPageFinished = { url, back ->
                addressText = url
                canGoBack = back
                settings.lastUrl = url
            },
            onProgress = { pageProgress = it },
        ).apply { loadUrl(settings.lastUrl) }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            webView.loadUrl(incomingUrl)
            onIncomingUrlHandled()
        }
    }

    // Follow the reader in the browser when it moves on to the next chapter.
    LaunchedEffect(state.chapterUrl) {
        val url = state.chapterUrl ?: return@LaunchedEffect
        if (state.status != ReaderStatus.IDLE && url.trimEnd('/') != webView.url?.trimEnd('/')) {
            webView.loadUrl(url)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.address_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    webView.loadUrl(normalizeAddress(addressText))
                    focusManager.clearFocus()
                }),
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
        if (pageProgress < 100) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())

        PlayerPanel(
            state = state,
            onReadPage = { readCurrentPage(context, webView) },
            onCommand = { action -> ReaderService.send(context, action) },
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onChanged = {
                if (state.status != ReaderStatus.IDLE) {
                    ReaderService.send(context, ReaderService.ACTION_SETTINGS_CHANGED)
                }
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun PlayerPanel(
    state: ReaderUiState,
    onReadPage: () -> Unit,
    onCommand: (String) -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (state.status == ReaderStatus.IDLE) {
                Button(onClick = onReadPage, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.read_this_page))
                }
                return@Column
            }

            Text(
                text = state.chapterTitle ?: state.chapterUrl.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.status == ReaderStatus.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.status == ReaderStatus.LOADING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            } else if (state.currentText.isNotEmpty()) {
                Text(
                    text = state.currentText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(R.drawable.ic_rewind, R.string.action_prev_paragraph) {
                    onCommand(ReaderService.ACTION_PREV_PARAGRAPH)
                }
                val playing = state.status == ReaderStatus.PLAYING
                FilledIconButton(
                    onClick = { onCommand(ReaderService.ACTION_TOGGLE) },
                    modifier = Modifier.size(56.dp),
                    enabled = state.status != ReaderStatus.LOADING,
                ) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                    )
                }
                ControlButton(R.drawable.ic_fast_forward, R.string.action_next_paragraph) {
                    onCommand(ReaderService.ACTION_NEXT_PARAGRAPH)
                }
                ControlButton(R.drawable.ic_skip_next, R.string.action_next_chapter) {
                    onCommand(ReaderService.ACTION_NEXT_CHAPTER)
                }
                ControlButton(R.drawable.ic_stop, R.string.action_stop) {
                    onCommand(ReaderService.ACTION_STOP)
                }
            }
            TextButton(onClick = onReadPage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.read_this_page_instead))
            }
        }
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
private fun statusLine(state: ReaderUiState): String = when (state.status) {
    ReaderStatus.LOADING -> stringResource(R.string.status_loading)
    ReaderStatus.ERROR, ReaderStatus.FINISHED -> state.message.orEmpty()
    else -> buildString {
        append(stringResource(R.string.status_position, state.position, state.total))
        if (state.chaptersRead > 0) {
            append(" · ")
            append(stringResource(R.string.status_chapters_read, state.chaptersRead))
        }
    }
}

@Composable
private fun SettingsDialog(settings: AppSettings, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }
    var mode by remember { mutableStateOf(settings.nextMode) }
    var autoContinue by remember { mutableStateOf(settings.autoContinue) }
    var readTitle by remember { mutableStateOf(settings.readTitle) }
    var limit by remember { mutableIntStateOf(settings.chapterLimit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.setting_rate, rate))
                Slider(
                    value = rate,
                    onValueChange = { rate = (it * 10).roundToInt() / 10f },
                    onValueChangeFinished = { settings.speechRate = rate; onChanged() },
                    valueRange = 0.5f..3f,
                )
                Text(stringResource(R.string.setting_pitch, pitch))
                Slider(
                    value = pitch,
                    onValueChange = { pitch = (it * 10).roundToInt() / 10f },
                    onValueChangeFinished = { settings.pitch = pitch; onChanged() },
                    valueRange = 0.5f..2f,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(stringResource(R.string.setting_auto_continue), autoContinue) {
                    autoContinue = it; settings.autoContinue = it
                }
                SwitchRow(stringResource(R.string.setting_read_title), readTitle) {
                    readTitle = it; settings.readTitle = it
                }

                Text(stringResource(R.string.setting_next_mode), Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                listOf(
                    NextMode.AUTO to R.string.mode_auto,
                    NextMode.LINK to R.string.mode_link,
                    NextMode.URL_INCREMENT to R.string.mode_url,
                ).forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == value, onClick = { mode = value; settings.nextMode = value }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == value, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    if (limit == 0) stringResource(R.string.setting_limit_off)
                    else stringResource(R.string.setting_limit, limit),
                )
                Slider(
                    value = limit.toFloat(),
                    onValueChange = { limit = it.roundToInt() },
                    onValueChangeFinished = { settings.chapterLimit = limit },
                    valueRange = 0f..20f,
                    steps = 19,
                )

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openTtsSettings(context) }) {
                    Text(stringResource(R.string.setting_open_tts))
                }
            }
        },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// -------------------------------------------------------------------- helpers

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, Boolean) -> Unit,
    onProgress: (Int) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPageStarted(url)
        override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
            onPageFinished(url, view.canGoBack())
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    }
}

/** Reads the page as the browser shows it (works on sites with bot checks and scripts). */
private fun readCurrentPage(context: Context, webView: WebView) {
    val url = webView.url ?: return
    webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
        val html = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
        if (html.isNullOrBlank()) {
            ReaderService.send(context, ReaderService.ACTION_PLAY_URL, url)
        } else {
            ReaderState.pendingPage = url to html
            ReaderService.send(context, ReaderService.ACTION_PLAY_PENDING_PAGE)
        }
    }
}

private fun normalizeAddress(input: String): String {
    val text = input.trim()
    return when {
        text.startsWith("http://") || text.startsWith("https://") -> text
        text.contains('.') && !text.contains(' ') -> "https://$text"
        else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8")
    }
}

private fun openTtsSettings(context: Context) {
    val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
