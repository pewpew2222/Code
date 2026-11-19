package com.chaptervoice.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.core.BookKey
import com.chaptervoice.app.core.TextCleaner.Replacement

/** General "say X as Y" rules, the "No. 1" rule, and the books that have their own rules. */
@Composable
internal fun PronunciationSettings(settings: AppSettings, onChanged: () -> Unit) {
    var rules by remember { mutableStateOf(settings.pronunciationRules) }
    var numberRule by remember { mutableStateOf(settings.numberRule) }
    var books by remember { mutableStateOf(settings.booksWithRules()) }
    var editingBook by remember { mutableStateOf<String?>(null) }

    Text(stringResource(R.string.setting_pronunciation), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    Text(
        stringResource(R.string.setting_pronunciation_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RuleEditor(rules) { newRules ->
        rules = newRules
        settings.pronunciationRules = newRules
        onChanged()
    }

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

    if (books.isNotEmpty()) {
        Text(stringResource(R.string.book_rules_list), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        books.forEach { (key, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.book_rules_row, BookKey.displayName(key), count),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editingBook = key }) { Text(stringResource(R.string.edit)) }
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

/** Pronunciation rules that only apply to one book. */
@Composable
internal fun BookRulesDialog(settings: AppSettings, bookKey: String, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var rules by remember(bookKey) { mutableStateOf(settings.bookRules(bookKey)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.book_rules_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(BookKey.displayName(bookKey), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.book_rules_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                RuleEditor(rules) { newRules ->
                    rules = newRules
                    settings.setBookRules(bookKey, newRules)
                    onChanged()
                }
            }
        },
    )
}

/** A list of "from → to" rules with delete buttons, and fields to add one. */
@Composable
private fun RuleEditor(rules: List<Replacement>, onRulesChange: (List<Replacement>) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    rules.forEach { rule ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${rule.from}  →  ${rule.to}", modifier = Modifier.weight(1f))
            IconButton(onClick = { onRulesChange(rules - rule) }) {
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
            onRulesChange(rules.filterNot { it.from.equals(newRule.from, ignoreCase = true) } + newRule)
            from = ""
            to = ""
        },
        enabled = from.isNotBlank(),
    ) { Text(stringResource(R.string.pronunciation_add)) }
}
