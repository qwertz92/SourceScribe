package app.sourcescribe.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sourcescribe.R

private sealed interface HelpRow {
    data class Header(val section: HelpSection) : HelpRow
    data class Entry(val topic: HelpTopic) : HelpRow
}

/**
 * The in-app glossary. Every explanation in the app lives here exactly once; controls link into it
 * instead of carrying their own prose.
 */
@Composable
internal fun HelpScreen(focus: HelpTopic?, focusConsumed: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val titles = HelpTopic.entries.associateWith { stringResource(it.title) }
    val bodies = HelpTopic.entries.associateWith { stringResource(it.body) }
    val sectionTitles = HelpSection.entries.associateWith { stringResource(it.title) }

    val matching = HelpTopic.entries.filter { topic ->
        query.isBlank() ||
            titles.getValue(topic).contains(query, ignoreCase = true) ||
            bodies.getValue(topic).contains(query, ignoreCase = true) ||
            sectionTitles.getValue(topic.section).contains(query, ignoreCase = true)
    }
    val rows = remember(matching) {
        buildList {
            for (section in HelpSection.entries) {
                val entries = matching.filter { it.section == section }
                if (entries.isEmpty()) continue
                add(HelpRow.Header(section))
                entries.forEach { add(HelpRow.Entry(it)) }
            }
        }
    }

    LaunchedEffect(focus, rows) {
        val topic = focus ?: return@LaunchedEffect
        query = ""
        open = topic.name
        val index = rows.indexOfFirst { it is HelpRow.Entry && it.topic == topic }
        // One leading item holds the search field, so the row index is offset by one.
        if (index >= 0) listState.scrollToItem(index + 2)
        focusConsumed()
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.help_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.help_search)) })
        }
        if (rows.isEmpty()) item { Text(stringResource(R.string.no_matches)) }
        items(rows.size, key = { index ->
            when (val row = rows[index]) {
                is HelpRow.Header -> "section-${row.section.name}"
                is HelpRow.Entry -> "topic-${row.topic.name}"
            }
        }) { index ->
            when (val row = rows[index]) {
                is HelpRow.Header -> Text(sectionTitles.getValue(row.section),
                    Modifier.padding(top = 12.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                is HelpRow.Entry -> HelpEntry(
                    title = titles.getValue(row.topic),
                    body = bodies.getValue(row.topic),
                    expanded = open == row.topic.name,
                    toggle = { open = if (open == row.topic.name) null else row.topic.name },
                )
            }
        }
    }
}

@Composable
private fun HelpEntry(title: String, body: String, expanded: Boolean, toggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Surface(onClick = toggle, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null,
                        modifier = Modifier.size(24.dp).rotate(rotation))
                }
            }
            if (expanded) Text(body, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
