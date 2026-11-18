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
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }

    // A separate TTS instance, only to list voices and play the sample.
    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                voices = runCatching { instance?.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                ready = true
            }
        }
        instance
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    // "Automatic" picks voices for English, the most common language of web novels.
    val voiceLanguage = if (language == "auto") "en" else language
    val languageVoices = voices
        .filter { it.locale.language == Locale.forLanguageTag(voiceLanguage).language }
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
