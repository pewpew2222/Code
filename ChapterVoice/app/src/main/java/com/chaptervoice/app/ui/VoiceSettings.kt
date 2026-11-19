package com.chaptervoice.app.ui

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import java.util.Locale

private val LANGUAGES = listOf("auto", "en", "sv", "zh", "ko", "ja")

/** Reading language and voice, with a button to hear the voice. */
@Composable
internal fun VoiceSettings(settings: AppSettings, onChanged: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(settings.ttsLanguage) }
    var engine by remember { mutableStateOf(settings.ttsEngine) }
    var engines by remember { mutableStateOf<List<TextToSpeech.EngineInfo>>(emptyList()) }
    var voices by remember(engine) { mutableStateOf<List<Voice>>(emptyList()) }
    var ready by remember(engine) { mutableStateOf(false) }
    var showEngines by remember { mutableStateOf(false) }

    // A separate TTS instance of the chosen engine, only to list voices and play the sample.
    val tts = remember(engine) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context, { status ->
            val current = instance
            if (status == TextToSpeech.SUCCESS && current != null) {
                voices = runCatching { current.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                engines = runCatching { current.engines.orEmpty() }.getOrDefault(emptyList())
            }
            ready = true
        }, engine.ifEmpty { null })
        instance
    }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }

    // ------------------------------------------------------------ engine
    Text(stringResource(R.string.setting_engine), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    val defaultEngine = runCatching { tts.defaultEngine }.getOrNull().orEmpty()
    fun engineLabel(pkg: String): String = engines.firstOrNull { it.name == pkg }?.label ?: pkg
    Box {
        OutlinedButton(onClick = { showEngines = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (engine.isEmpty()) defaultEngineText(engineLabel(defaultEngine)) else engineLabel(engine),
            )
        }
        DropdownMenu(expanded = showEngines, onDismissRequest = { showEngines = false }) {
            DropdownMenuItem(
                text = { Text(defaultEngineText(engineLabel(defaultEngine))) },
                onClick = { engine = ""; settings.ttsEngine = ""; showEngines = false; onChanged() },
            )
            engines.forEach { info ->
                DropdownMenuItem(
                    text = { Text(info.label) },
                    onClick = { engine = info.name; settings.ttsEngine = info.name; showEngines = false; onChanged() },
                )
            }
        }
    }

    // "Automatic" picks voices for English, the most common language of web novels.
    val voiceLanguage = if (language == "auto") "en" else language
    // Engines that report no voice for the language (or odd language codes) show all their voices.
    val languageVoices = voices
        .filter { sameLanguage(it.locale, voiceLanguage) }
        .ifEmpty { voices }
        .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.locale.toLanguageTag() }, { it.name }))
    var voiceName by remember(voiceLanguage) { mutableStateOf(settings.ttsVoice(voiceLanguage)) }
    var showVoices by remember { mutableStateOf(false) }

    Text(stringResource(R.string.setting_language), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    LANGUAGES.forEach { tag ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = language == tag, onClick = {
                    language = tag
                    settings.ttsLanguage = tag
                    onChanged()
                }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = language == tag, onClick = null)
            Text(
                if (tag == "auto") stringResource(R.string.language_auto) else languageName(tag),
                Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }

    Text(
        stringResource(R.string.setting_voice, languageName(voiceLanguage)),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
    when {
        !ready -> Text(stringResource(R.string.voices_loading), style = MaterialTheme.typography.bodySmall)
        languageVoices.isEmpty() -> Text(
            stringResource(R.string.voices_none, languageName(voiceLanguage)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Column {
            Box {
                OutlinedButton(onClick = { showVoices = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        languageVoices.firstOrNull { it.name == voiceName }?.let { voiceLabel(it) }
                            ?: stringResource(R.string.voice_default),
                    )
                }
                DropdownMenu(expanded = showVoices, onDismissRequest = { showVoices = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.voice_default)) },
                        onClick = {
                            voiceName = ""
                            settings.setTtsVoice(voiceLanguage, "")
                            showVoices = false
                            onChanged()
                        },
                    )
                    languageVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voiceLabel(voice)) },
                            onClick = {
                                voiceName = voice.name
                                settings.setTtsVoice(voiceLanguage, voice.name)
                                showVoices = false
                                onChanged()
                            },
                        )
                    }
                }
            }
            val sample = stringResource(R.string.voice_sample)
            TextButton(onClick = {
                tts.setLanguage(Locale.forLanguageTag(voiceLanguage))
                languageVoices.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
                tts.setSpeechRate(settings.speechRate)
                tts.setPitch(settings.pitch)
                tts.speak(sampleFor(voiceLanguage, sample), TextToSpeech.QUEUE_FLUSH, null, "sample")
            }) { Text(stringResource(R.string.voice_try)) }
        }
    }
}

@Composable
private fun defaultEngineText(label: String): String =
    if (label.isBlank()) stringResource(R.string.engine_default_plain) else stringResource(R.string.engine_default, label)

/** "en" matches voices tagged "en-US" and engines that use three letters ("eng"). */
private fun sameLanguage(locale: Locale, tag: String): Boolean {
    val wanted = Locale.forLanguageTag(tag)
    if (locale.language.equals(wanted.language, ignoreCase = true)) return true
    return runCatching { locale.isO3Language.equals(wanted.isO3Language, ignoreCase = true) }.getOrDefault(false)
}

private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/** "English (United States) · en-us-x-iol-local · offline" */
private fun voiceLabel(voice: Voice): String {
    val region = voice.locale.getDisplayCountry(Locale.getDefault()).ifEmpty { voice.locale.toLanguageTag() }
    val where = if (voice.isNetworkConnectionRequired) "online" else "offline"
    return "$region · ${voice.name} · $where"
}

private fun sampleFor(language: String, fallback: String): String = when (language) {
    "en" -> "This is how the chapter will sound."
    "zh" -> "这是章节朗读的声音。"
    "ko" -> "이 목소리로 챕터를 읽어 드립니다."
    "ja" -> "この声で章を読み上げます。"
    else -> fallback
}
