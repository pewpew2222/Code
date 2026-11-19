package com.chaptervoice.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AdBlocker
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.core.NextMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsDialog(settings: AppSettings, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }
    var mode by remember { mutableStateOf(settings.nextMode) }
    var autoContinue by remember { mutableStateOf(settings.autoContinue) }
    var readTitle by remember { mutableStateOf(settings.readTitle) }
    var limit by remember { mutableIntStateOf(settings.chapterLimit) }
    var adBlock by remember { mutableStateOf(settings.adBlock) }
    var removeOverlays by remember { mutableStateOf(settings.removeOverlays) }
    var preload by remember { mutableIntStateOf(settings.preloadCount) }
    var deleteAfterRead by remember { mutableStateOf(settings.deleteAfterRead) }

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
                PauseSlider(
                    label = { stringResource(R.string.setting_paragraph_pause, it) },
                    initial = settings.paragraphPauseMs,
                    maxMs = 2000,
                    stepMs = 50,
                    onDone = { settings.paragraphPauseMs = it; onChanged() },
                )
                PauseSlider(
                    label = {
                        if (it == 0) stringResource(R.string.setting_sentence_pause_off)
                        else stringResource(R.string.setting_sentence_pause, it)
                    },
                    initial = settings.sentencePauseMs,
                    maxMs = 300,
                    stepMs = 10,
                    onDone = { settings.sentencePauseMs = it; onChanged() },
                )
                VoiceSettings(settings, onChanged)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                PronunciationSettings(settings, onChanged)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(stringResource(R.string.setting_auto_continue), autoContinue) {
                    autoContinue = it; settings.autoContinue = it
                }
                SwitchRow(stringResource(R.string.setting_read_title), readTitle) {
                    readTitle = it; settings.readTitle = it
                }
                SwitchRow(stringResource(R.string.setting_ad_block, AdBlocker.domainCount), adBlock) {
                    adBlock = it; settings.adBlock = it
                }
                SwitchRow(stringResource(R.string.setting_remove_overlays), removeOverlays) {
                    removeOverlays = it; settings.removeOverlays = it
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

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    if (preload == 0) stringResource(R.string.setting_preload_off)
                    else stringResource(R.string.setting_preload, preload),
                )
                Slider(
                    value = preload.toFloat(),
                    onValueChange = { preload = it.roundToInt() },
                    onValueChangeFinished = { settings.preloadCount = preload },
                    valueRange = 0f..20f,
                    steps = 19,
                )
                SwitchRow(stringResource(R.string.setting_delete_after_read), deleteAfterRead) {
                    deleteAfterRead = it; settings.deleteAfterRead = it
                }

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

private fun openTtsSettings(context: Context) {
    val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** A pause slider from 0 to [maxMs] milliseconds in steps of [stepMs]. */
@Composable
private fun PauseSlider(label: @Composable (Int) -> String, initial: Int, maxMs: Int, stepMs: Int, onDone: (Int) -> Unit) {
    var value by remember { mutableIntStateOf(initial.coerceIn(0, maxMs)) }
    Text(label(value))
    Slider(
        value = value.toFloat(),
        onValueChange = { value = (it / stepMs).roundToInt() * stepMs },
        onValueChangeFinished = { onDone(value) },
        valueRange = 0f..maxMs.toFloat(),
        steps = maxMs / stepMs - 1,
    )
}
