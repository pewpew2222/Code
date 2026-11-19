package com.chaptervoice.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.core.TextCleaner.Replacement

/** "Say X as Y" rules, e.g. "exp" -> "experience", and the "No. 1" -> "number 1" rule. */
@Composable
internal fun PronunciationSettings(settings: AppSettings, onChanged: () -> Unit) {
    var rules by remember { mutableStateOf(settings.pronunciationRules) }
    var numberRule by remember { mutableStateOf(settings.numberRule) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    fun save(newRules: List<Replacement>) {
        rules = newRules
        settings.pronunciationRules = newRules
        onChanged()
    }

    Text(stringResource(R.string.setting_pronunciation), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    Text(
        stringResource(R.string.setting_pronunciation_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rules.forEach { rule ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${rule.from}  →  ${rule.to}", modifier = Modifier.weight(1f))
            IconButton(onClick = { save(rules - rule) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = from,
            onValueChange = { from = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.pronunciation_from)) },
            modifier = Modifier.weight(1f),
        )
        Text("→", modifier = Modifier.padding(horizontal = 6.dp))
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.pronunciation_to)) },
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(
        onClick = {
            val newRule = Replacement(from.trim(), to.trim())
            save(rules.filterNot { it.from.equals(newRule.from, ignoreCase = true) } + newRule)
            from = ""
            to = ""
        },
        enabled = from.isNotBlank(),
    ) { Text(stringResource(R.string.pronunciation_add)) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setting_number_rule), modifier = Modifier.weight(1f))
        Switch(
            checked = numberRule,
            onCheckedChange = {
                numberRule = it
                settings.numberRule = it
                onChanged()
            },
        )
    }
}
