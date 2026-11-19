package com.chaptervoice.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AdBlocker
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.core.BookKey
import com.chaptervoice.app.core.NextMode
import com.chaptervoice.app.core.TextCleaner.Replacement
import java.util.Locale
import kotlin.math.roundToInt

private val LANGUAGES = listOf("auto", "en", "sv", "zh", "ko", "ja")
private val SLEEP_OPTIONS = listOf(0, 1, 2, 3, 5, 10, 20)

@Composable
internal fun SettingsScreen(settings: AppSettings, onChanged: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            AppearanceGroup(settings)
            VoiceGroup(settings, onChanged)
            ReadingGroup(settings, onChanged)
            TextRulesGroup(settings, onChanged)
            OfflineGroup(settings)
            BrowserGroup(settings)
            SettingsGroup(stringResource(R.string.group_other), R.drawable.ic_settings) {
                val context = LocalContext.current
                ValueRow(stringResource(R.string.setting_open_tts), stringResource(R.string.tts_system_hint), onClick = { openTtsSettings(context) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------------ appearance

@Composable
private fun AppearanceGroup(settings: AppSettings) {
    var mode by remember { mutableStateOf(settings.themeMode) }
    var accent by remember { mutableStateOf(settings.accent) }
    SettingsGroup(stringResource(R.string.section_appearance), R.drawable.ic_palette) {
        GroupBlock {
            Text(stringResource(R.string.setting_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
            val modes = listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (id, label) ->
                    SegmentedButton(
                        selected = mode == id,
                        onClick = { mode = id; settings.themeMode = id },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        icon = {},
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
        }
        RowDivider()
        GroupBlock {
            Text(stringResource(R.string.setting_accent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 10.dp))
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENTS.forEach { option ->
                    val selected = accent == option.id
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .padding(if (selected) 5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(if (dark) option.dark else option.light)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { accent = option.id; settings.accent = option.id })
                            .semantics { contentDescription = option.label },
                    )
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RowDivider()
            SwitchRow(stringResource(R.string.accent_dynamic), checked = accent == "dynamic") { on ->
                val value = if (on) "dynamic" else "blue"
                accent = value
                settings.accent = value
            }
        }
    }
}

// ------------------------------------------------------------------------ voice

@Composable
private fun VoiceGroup(settings: AppSettings, onChanged: () -> Unit) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf(settings.ttsEngine) }
    var language by remember { mutableStateOf(settings.ttsLanguage) }
    var engines by remember { mutableStateOf<List<TextToSpeech.EngineInfo>>(emptyList()) }
    var voices by remember(engine) { mutableStateOf<List<Voice>>(emptyList()) }
    var ready by remember(engine) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }
    var paragraphPause by remember { mutableIntStateOf(settings.paragraphPauseMs) }
    var sentencePause by remember { mutableIntStateOf(settings.sentencePauseMs) }

    // A TTS instance of the chosen engine, to list engines and voices and play samples.
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

    val defaultEngineLabel = engines.firstOrNull { it.name == runCatching { tts.defaultEngine }.getOrNull() }?.label.orEmpty()
    val engineText = if (engine.isEmpty()) {
        if (defaultEngineLabel.isBlank()) stringResource(R.string.engine_default_plain) else stringResource(R.string.engine_default, defaultEngineLabel)
    } else {
        engines.firstOrNull { it.name == engine }?.label ?: engine
    }
    val voiceLanguage = if (language == "auto") "en" else language
    val languageVoices = voices
        .filter { sameLanguage(it.locale, voiceLanguage) }
        .ifEmpty { voices }
        .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.locale.toLanguageTag() }, { it.name }))
    var voiceName by remember(voiceLanguage) { mutableStateOf(settings.ttsVoice(voiceLanguage)) }
    val voiceText = when {
        !ready -> stringResource(R.string.voices_loading)
        voiceName.isEmpty() -> stringResource(R.string.voice_default)
        else -> languageVoices.firstOrNull { it.name == voiceName }?.let { voiceLabel(it) } ?: voiceName
    }

    SettingsGroup(stringResource(R.string.group_voice), R.drawable.ic_mic) {
        ValueRow(stringResource(R.string.label_engine), engineText, onClick = { sheet = "engine" })
        RowDivider()
        ValueRow(
            stringResource(R.string.label_language),
            if (language == "auto") stringResource(R.string.language_auto) else languageName(language),
            onClick = { sheet = "language" },
        )
        RowDivider()
        ValueRow(stringResource(R.string.setting_voice, languageName(voiceLanguage)), voiceText, onClick = { sheet = "voice" })
        RowDivider()
        SliderRow(
            stringResource(R.string.label_speed), formatTimes(rate), rate, 0.5f..3f,
            onChange = { rate = (it * 10).roundToInt() / 10f },
            onDone = { settings.speechRate = rate; onChanged() },
        )
        SliderRow(
            stringResource(R.string.label_pitch), "%.1f".format(pitch).replace('.', ','), pitch, 0.5f..2f,
            onChange = { pitch = (it * 10).roundToInt() / 10f },
            onDone = { settings.pitch = pitch; onChanged() },
        )
        RowDivider()
        SliderRow(
            stringResource(R.string.label_paragraph_pause), stringResource(R.string.value_ms, paragraphPause),
            paragraphPause.toFloat(), 0f..2000f,
            onChange = { paragraphPause = (it / 50).roundToInt() * 50 },
            onDone = { settings.paragraphPauseMs = paragraphPause; onChanged() },
        )
        SliderRow(
            stringResource(R.string.label_sentence_pause),
            if (sentencePause == 0) stringResource(R.string.value_off) else stringResource(R.string.value_ms, sentencePause),
            sentencePause.toFloat(), 0f..300f,
            onChange = { sentencePause = (it / 10).roundToInt() * 10 },
            onDone = { settings.sentencePauseMs = sentencePause; onChanged() },
        )
    }

    when (sheet) {
        "engine" -> ChoiceSheet(
            title = stringResource(R.string.label_engine),
            options = listOf("" to (if (defaultEngineLabel.isBlank()) stringResource(R.string.engine_default_plain) else stringResource(R.string.engine_default, defaultEngineLabel))) +
                engines.map { it.name to it.label },
            selected = engine,
            onSelect = { engine = it; settings.ttsEngine = it; onChanged() },
            onDismiss = { sheet = null },
        )
        "language" -> ChoiceSheet(
            title = stringResource(R.string.label_language),
            options = LANGUAGES.map { it to if (it == "auto") stringResource(R.string.language_auto) else languageName(it) },
            selected = language,
            onSelect = { language = it; settings.ttsLanguage = it; onChanged() },
            onDismiss = { sheet = null },
        )
        "voice" -> {
            val sample = stringResource(R.string.voice_sample)
            Sheet(stringResource(R.string.setting_voice, languageName(voiceLanguage)), onDismiss = { sheet = null }) {
                if (ready && languageVoices.isEmpty()) {
                    Text(stringResource(R.string.voices_none, languageName(voiceLanguage)), color = MaterialTheme.colorScheme.error)
                }
                (listOf<Voice?>(null) + languageVoices).forEach { voice ->
                    val name = voice?.name.orEmpty()
                    val selected = voiceName == name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                voiceName = name
                                settings.setTtsVoice(voiceLanguage, name)
                                onChanged()
                            })
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            voice?.let { voiceLabel(it) } ?: stringResource(R.string.voice_default),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            tts.setLanguage(Locale.forLanguageTag(voiceLanguage))
                            if (voice != null) tts.voice = voice
                            tts.setSpeechRate(settings.speechRate)
                            tts.setPitch(settings.pitch)
                            tts.speak(sampleFor(voiceLanguage, sample), TextToSpeech.QUEUE_FLUSH, null, "sample")
                        }) {
                            Icon(painterResource(R.drawable.ic_volume), contentDescription = stringResource(R.string.voice_try))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ reading

@Composable
private fun ReadingGroup(settings: AppSettings, onChanged: () -> Unit) {
    var autoContinue by remember { mutableStateOf(settings.autoContinue) }
    var readTitle by remember { mutableStateOf(settings.readTitle) }
    var mode by remember { mutableStateOf(settings.nextMode) }
    var limit by remember { mutableIntStateOf(settings.chapterLimit) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val modes = listOf(
        NextMode.AUTO to stringResource(R.string.mode_auto),
        NextMode.LINK to stringResource(R.string.mode_link),
        NextMode.URL_INCREMENT to stringResource(R.string.mode_url),
    )

    SettingsGroup(stringResource(R.string.group_reading), R.drawable.ic_headphones) {
        SwitchRow(stringResource(R.string.setting_auto_continue), checked = autoContinue) { autoContinue = it; settings.autoContinue = it }
        RowDivider()
        SwitchRow(stringResource(R.string.setting_read_title), checked = readTitle) { readTitle = it; settings.readTitle = it; onChanged() }
        RowDivider()
        ValueRow(stringResource(R.string.setting_next_mode), modes.first { it.first == mode }.second, onClick = { sheet = "mode" })
        RowDivider()
        ValueRow(
            stringResource(R.string.label_sleep),
            if (limit == 0) stringResource(R.string.value_off) else stringResource(R.string.setting_limit, limit),
            onClick = { sheet = "sleep" },
        )
    }
    when (sheet) {
        "mode" -> ChoiceSheet(
            title = stringResource(R.string.setting_next_mode),
            options = modes,
            selected = mode,
            onSelect = { mode = it; settings.nextMode = it },
            onDismiss = { sheet = null },
        )
        "sleep" -> ChoiceSheet(
            title = stringResource(R.string.label_sleep),
            options = SLEEP_OPTIONS.map { it to if (it == 0) stringResource(R.string.value_off) else stringResource(R.string.setting_limit, it) },
            selected = limit,
            onSelect = { limit = it; settings.chapterLimit = it },
            onDismiss = { sheet = null },
        )
    }
}

// ------------------------------------------------------------------------ text rules

@Composable
private fun TextRulesGroup(settings: AppSettings, onChanged: () -> Unit) {
    val switches by AppSettings.rulesSwitches.collectAsState()
    val globalOn = remember(switches) { settings.textRulesEnabled }
    var rules by remember { mutableStateOf(settings.pronunciationRules) }
    var numberRule by remember { mutableStateOf(settings.numberRule) }
    var books by remember { mutableStateOf(settings.booksWithRules()) }
    var editingBook by remember { mutableStateOf<String?>(null) }
    var editingRules by remember { mutableStateOf(false) }

    SettingsGroup(stringResource(R.string.group_text_rules), R.drawable.ic_spellcheck) {
        SwitchRow(stringResource(R.string.rules_global), stringResource(R.string.rules_toggle_hint), checked = globalOn) {
            settings.textRulesEnabled = it
            onChanged()
        }
        RowDivider()
        ValueRow(
            stringResource(R.string.label_rules),
            if (rules.isEmpty()) stringResource(R.string.value_no_rules) else rules.joinToString(", ") { "${it.from} → ${it.to}" },
            onClick = { editingRules = true },
        )
        RowDivider()
        SwitchRow(stringResource(R.string.setting_number_rule), checked = numberRule) {
            numberRule = it
            settings.numberRule = it
            onChanged()
        }
        books.forEach { (key, count) ->
            RowDivider()
            ValueRow(BookKey.displayName(key), stringResource(R.string.value_rules_count, count), onClick = { editingBook = key })
        }
    }

    if (editingRules) {
        Sheet(stringResource(R.string.label_rules), onDismiss = { editingRules = false }) {
            Text(stringResource(R.string.setting_pronunciation_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            RuleEditor(rules) { newRules ->
                rules = newRules
                settings.pronunciationRules = newRules
                onChanged()
            }
        }
    }
    editingBook?.let { key ->
        BookRulesDialog(
            settings = settings,
            bookKey = key,
            onChanged = {
                books = settings.booksWithRules()
                onChanged()
            },
            onDismiss = { editingBook = null },
        )
    }
}

/** Rules for one book, and its own on/off switch, in a bottom sheet. */
@Composable
internal fun BookRulesDialog(settings: AppSettings, bookKey: String, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var rules by remember(bookKey) { mutableStateOf(settings.bookRules(bookKey)) }
    val switches by AppSettings.rulesSwitches.collectAsState()
    val bookOn = remember(switches, bookKey) { settings.bookRulesEnabled(bookKey) }
    Sheet(stringResource(R.string.book_rules_title), onDismiss) {
        Text(BookKey.displayName(bookKey), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.book_rules_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
            SwitchRow(stringResource(R.string.rules_book), checked = bookOn) { settings.setBookRulesEnabled(bookKey, it); onChanged() }
        }
        Spacer(Modifier.height(12.dp))
        RuleEditor(rules) { newRules ->
            rules = newRules
            settings.setBookRules(bookKey, newRules)
            onChanged()
        }
    }
}

/** "from → to" rules as cards with a remove button, and fields to add one. */
@Composable
private fun RuleEditor(rules: List<Replacement>, onRulesChange: (List<Replacement>) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rules.forEach { rule ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.from, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("→", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
                    Text(rule.to, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRulesChange(rules - rule) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = from, onValueChange = { from = it }, singleLine = true,
                label = { Text(stringResource(R.string.pronunciation_from)) }, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = to, onValueChange = { to = it }, singleLine = true,
                label = { Text(stringResource(R.string.pronunciation_to)) }, modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = {
                val newRule = Replacement(from.trim(), to.trim())
                onRulesChange(rules.filterNot { it.from.equals(newRule.from, ignoreCase = true) } + newRule)
                from = ""
                to = ""
            },
            enabled = from.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.pronunciation_add)) }
    }
}

// ------------------------------------------------------------------------ offline and browser

@Composable
private fun OfflineGroup(settings: AppSettings) {
    var preload by remember { mutableIntStateOf(settings.preloadCount) }
    var deleteAfterRead by remember { mutableStateOf(settings.deleteAfterRead) }
    SettingsGroup(stringResource(R.string.group_offline), R.drawable.ic_download) {
        SliderRow(
            stringResource(R.string.label_preload),
            if (preload == 0) stringResource(R.string.value_off) else stringResource(R.string.value_chapters, preload),
            preload.toFloat(), 0f..20f,
            onChange = { preload = it.roundToInt() },
            onDone = { settings.preloadCount = preload },
        )
        RowDivider()
        SwitchRow(stringResource(R.string.setting_delete_after_read), checked = deleteAfterRead) {
            deleteAfterRead = it
            settings.deleteAfterRead = it
        }
    }
}

@Composable
private fun BrowserGroup(settings: AppSettings) {
    var adBlock by remember { mutableStateOf(settings.adBlock) }
    var removeOverlays by remember { mutableStateOf(settings.removeOverlays) }
    SettingsGroup(stringResource(R.string.group_browser), R.drawable.ic_shield) {
        SwitchRow(
            stringResource(R.string.label_ad_block),
            stringResource(R.string.value_domains, AdBlocker.domainCount),
            checked = adBlock,
        ) { adBlock = it; settings.adBlock = it }
        RowDivider()
        SwitchRow(stringResource(R.string.setting_remove_overlays), checked = removeOverlays, enabled = adBlock) {
            removeOverlays = it
            settings.removeOverlays = it
        }
    }
}

// ------------------------------------------------------------------------ helpers

private fun openTtsSettings(context: Context) {
    val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun formatTimes(value: Float) = "%.1f×".format(value).replace('.', ',')

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

/** "USA · en-us-x-iol-local · offline" */
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
