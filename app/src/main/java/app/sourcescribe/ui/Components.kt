package app.sourcescribe.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.sourcescribe.R

/** The share of the window a dialog may occupy before its own content has to scroll. */
@Composable
internal fun dialogMaxHeight(fraction: Float): androidx.compose.ui.unit.Dp =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * fraction

/** Opens the glossary at one entry. Never changes the layout of the row it sits in. */
@Composable
internal fun InfoButton(topic: HelpTopic, openHelp: (HelpTopic) -> Unit) {
    val label = stringResource(R.string.help_open) + ": " + stringResource(topic.title)
    IconButton({ openHelp(topic) }, Modifier.size(40.dp)) {
        Icon(painterResource(R.drawable.ic_help), contentDescription = label,
            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A value picker that keeps its own height stable: the tallest option is measured up front, so
 * changing the selection never moves the controls below it.
 */
@Composable
internal fun <T> Choice(
    label: String,
    selected: String,
    options: List<T>,
    name: @Composable (T) -> String,
    enabled: Boolean = true,
    supporting: String? = null,
    info: HelpTopic? = null,
    openHelp: (HelpTopic) -> Unit = {},
    /** Richer text for the open list; the closed control keeps the short value so its height stays small. */
    optionName: (@Composable (T) -> String)? = null,
    choose: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val valueNames = options.map { name(it) }
    val optionNames = options.map { (optionName ?: name)(it) }
    val valueStyle = MaterialTheme.typography.bodyLarge
    val textMeasurer = rememberTextMeasurer()
    val maximumDialogHeight = dialogMaxHeight(0.7f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { role = Role.Button },
            shape = MaterialTheme.shapes.small,
            enabled = enabled,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Reserve the tallest option at the actual font/width so selection never moves its neighbours.
                        val valueHeightPx = remember(valueNames, selected, valueStyle, constraints.maxWidth, textMeasurer) {
                            (valueNames + selected).maxOf { value ->
                                textMeasurer.measure(value, valueStyle, constraints = Constraints(maxWidth = constraints.maxWidth)).size.height
                            }
                        }
                        Text(selected, Modifier.heightIn(min = with(LocalDensity.current) { valueHeightPx.toDp() }), style = valueStyle)
                    }
                }
                if (info != null) InfoButton(info, openHelp)
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        if (supporting != null) Text(supporting, Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (expanded && enabled) Dialog({ expanded = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = maximumDialogHeight).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(options.size) { index ->
                        TextButton({ choose(options[index]); expanded = false },
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(optionNames[index], Modifier.fillMaxWidth())
                        }
                    }
                }
                TextButton({ expanded = false }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
internal fun Toggle(
    @StringRes label: Int,
    checked: Boolean,
    enabled: Boolean = true,
    supporting: String? = null,
    info: HelpTopic? = null,
    openHelp: (HelpTopic) -> Unit = {},
    change: (Boolean) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = change),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), Modifier.weight(1f).padding(vertical = 12.dp))
            // The nested button consumes its own taps, so the explanation never flips the switch.
            if (info != null) InfoButton(info, openHelp)
            Switch(checked, null, enabled = enabled)
        }
        if (supporting != null) Text(supporting, Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionTitle(@StringRes label: Int, info: HelpTopic? = null, openHelp: (HelpTopic) -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge)
        if (info != null) InfoButton(info, openHelp)
    }
}

@Composable
internal fun ConfirmationDialog(title: String, explanation: String, close: () -> Unit, confirm: () -> Unit) {
    val maximumHeight = dialogMaxHeight(0.85f)
    Dialog(close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = maximumHeight).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(title, style = MaterialTheme.typography.titleLarge) }
                    item { Text(explanation) }
                    item { Button(confirm, Modifier.fillMaxWidth()) { Text(title) } }
                }
                TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/** A short status word on a coloured ground; height is fixed so a state change never reflows a card. */
@Composable
internal fun StatusChip(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Box(Modifier.heightIn(min = 28.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
