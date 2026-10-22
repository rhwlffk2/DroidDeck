package com.droiddeck.launcher.ui

import com.droiddeck.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.core.PhantomProcessLimit
import com.droiddeck.launcher.core.PhantomProcessStatus

@Composable
fun PhantomProcessGatePage(
    status: PhantomProcessStatus,
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onCopyCommand: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val hasToggle = PhantomProcessLimit.hasDeveloperToggle()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 620.dp && maxHeight < 500.dp
        SettingsPage(
            host = rememberMenuHost(),
            title = stringResource(R.string.gate_title),
            eyebrow = stringResource(R.string.gate_eyebrow),
            lede = if (compact) null else stringResource(R.string.gate_lede),
            onBack = onDismiss,
            scrollContent = true,
            compactLayout = compact,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 620.dp
                val fix: @Composable ColumnScope.() -> Unit = {
                    FixGroup(hasToggle, compact, onOpenDeveloperOptions)
                }
                val side: @Composable ColumnScope.() -> Unit = {
                    StatusGroup(status, compact)
                    OtherWays(hasToggle, compact, onOpenDeveloperOptions, onCopyCommand)
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                        Column(Modifier.weight(1.2f), content = fix)
                        Column(Modifier.weight(1f), content = side)
                    }
                } else {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                        fix()
                        side()
                    }
                }
            }
        }
    }
}

@Composable
private fun FixGroup(hasToggle: Boolean, compact: Boolean, onOpenDeveloperOptions: () -> Unit) {
    SettingsGroup(if (hasToggle) stringResource(R.string.setup_dev_options) else stringResource(R.string.gate_other_ways), compact = compact) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasToggle) {
                Body(stringResource(R.string.gate_toggle_steps))
            } else {
                Body(stringResource(R.string.gate_some_roms))
            }
            PrimaryButton(stringResource(R.string.gate_open_dev), compact = compact, onClick = onOpenDeveloperOptions)
        }
    }
}

@Composable
private fun StatusGroup(status: PhantomProcessStatus, compact: Boolean) {
    SettingsGroup(stringResource(R.string.gate_status), compact = compact) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(PhantomProcessLimit.title(LocalContext.current, status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Body(stringResource(R.string.gate_polling))
        }
    }
}

@Composable
private fun OtherWays(
    hasToggle: Boolean,
    compact: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    onCopyCommand: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.gate_other_ways), compact = compact) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Body(stringResource(R.string.gate_from_computer), modifier = Modifier.weight(1f))
            Text(if (open) "\u25b4" else "\u25be", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) Column(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasToggle) {
                SecondaryButton(stringResource(R.string.gate_open_dev), compact = true, onClick = onOpenDeveloperOptions)
            }
            Text(
                PhantomProcessLimit.adbCommand(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(stringResource(R.string.gate_copy_command), compact = true, onClick = onCopyCommand)
        }
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, error: Boolean = false, warn: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            error -> MaterialTheme.colorScheme.error
            warn -> androidx.compose.ui.graphics.Color(0xFFFFB86B)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
fun DeveloperDisplayChoiceDialog(
    displays: List<Pair<Int, String>>,
    onMainScreen: () -> Unit,
    onSecondaryScreen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gate_open_dev)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gate_choose_display))
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = onMainScreen) { Text(stringResource(R.string.gate_main_screen)) }
                displays.forEach { (id, label) ->
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { onSecondaryScreen(id) }) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
