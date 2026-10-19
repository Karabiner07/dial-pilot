package com.dialpilot.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dialpilot.R
import com.dialpilot.core.settings.DialConfig
import com.dialpilot.ui.components.ActionPickerDialog
import com.dialpilot.ui.components.ActionSettingsCard
import com.dialpilot.ui.components.AppIcon
import com.dialpilot.ui.components.AppPickerDialog
import com.dialpilot.ui.components.ClickableRow
import com.dialpilot.ui.components.SectionHeader
import com.dialpilot.ui.components.SwitchRow

/** Which picker is open. Rules are added in two steps: app, then action. */
private sealed interface Picker {
    data object DefaultAction : Picker
    data object NewRuleApp : Picker
    data class RuleAction(val packageName: String) : Picker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Picker?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { StatusCard(state, viewModel) }

            item { SectionHeader(stringResource(R.string.section_default_action)) }
            item {
                val action = viewModel.action(state.config.defaultActionId)
                ClickableRow(
                    title = action?.let { stringResource(it.titleRes) } ?: state.config.defaultActionId,
                    description = action?.let { stringResource(it.descriptionRes) },
                    onClick = { picker = Picker.DefaultAction },
                )
            }

            appRules(state, viewModel, onEdit = { picker = Picker.RuleAction(it) }, onAdd = {
                viewModel.loadApps()
                picker = Picker.NewRuleApp
            })

            item { SectionHeader(stringResource(R.string.section_action_settings)) }
            items(viewModel.actions.filter { it.settings.isNotEmpty() }, key = { it.id }) { action ->
                ActionSettingsCard(
                    action = action,
                    stored = state.config.actionValues[action.id].orEmpty(),
                    onChange = { key, value -> viewModel.setActionValue(action.id, key, value) },
                )
            }

            item { SectionHeader(stringResource(R.string.section_general)) }
            item { GeneralSettings(state, viewModel) }

            item { SectionHeader(stringResource(R.string.section_how_it_works)) }
            item {
                Text(
                    stringResource(R.string.how_it_works_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                )
            }
        }
    }

    when (val current = picker) {
        null -> Unit
        Picker.DefaultAction -> ActionPickerDialog(
            actions = viewModel.actions,
            selectedId = state.config.defaultActionId,
            onSelect = { viewModel.setDefaultAction(it.id); picker = null },
            onDismiss = { picker = null },
        )
        Picker.NewRuleApp -> AppPickerDialog(
            apps = apps,
            onSelect = { picker = Picker.RuleAction(it.packageName) },
            onDismiss = { picker = null },
        )
        is Picker.RuleAction -> ActionPickerDialog(
            actions = viewModel.actions,
            selectedId = state.config.appRules[current.packageName],
            onSelect = { viewModel.setAppRule(current.packageName, it.id); picker = null },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun StatusCard(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    if (!state.serviceEnabled) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text(stringResource(R.string.service_off_title), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.service_off_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text(stringResource(R.string.open_accessibility_settings))
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }) { Text(stringResource(R.string.open_app_info)) }
                }
            }
        }
        return
    }

    val engine = state.engine
    val activeAction = viewModel.action(engine.activeActionId)
    val headline = when {
        !state.config.enabled -> stringResource(R.string.status_paused)
        !engine.serviceRunning -> stringResource(R.string.status_waiting_service)
        activeAction != null -> stringResource(R.string.status_capturing, stringResource(activeAction.titleRes))
        else -> stringResource(R.string.status_passthrough)
    }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.master_switch), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.config.enabled, onCheckedChange = viewModel::setEnabled)
            }
            Text(headline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            engine.foregroundPackage?.let {
                StatusLine(stringResource(R.string.status_foreground_app, state.appLabels[it] ?: it))
            }
            StatusLine(
                if (engine.connectedHeadsets.isEmpty()) {
                    stringResource(R.string.status_no_headset)
                } else {
                    stringResource(R.string.status_headsets, engine.connectedHeadsets.joinToString())
                },
            )
            engine.lastEvent?.let { event ->
                val title = viewModel.action(event.actionId)?.let { stringResource(it.titleRes) } ?: event.actionId
                StatusLine(stringResource(R.string.status_last_event, event.steps, title))
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun LazyListScope.appRules(
    state: MainUiState,
    viewModel: MainViewModel,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    item { SectionHeader(stringResource(R.string.section_app_rules)) }
    val rules = state.config.appRules.entries.sortedBy { state.appLabels[it.key]?.lowercase() ?: it.key }
    if (rules.isEmpty()) {
        item {
            Text(
                stringResource(R.string.app_rules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
    items(rules, key = { "rule:${it.key}" }) { (packageName, actionId) ->
        ClickableRow(
            title = state.appLabels[packageName] ?: packageName,
            description = viewModel.action(actionId)?.let { stringResource(it.titleRes) } ?: actionId,
            onClick = { onEdit(packageName) },
            leading = { AppIcon(packageName) },
            trailing = {
                IconButton(onClick = { viewModel.removeAppRule(packageName) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_rule))
                }
            },
        )
    }
    item {
        TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.add_app_rule), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralSettings(state: MainUiState, viewModel: MainViewModel) {
    val config: DialConfig = state.config
    Column {
        SwitchRow(
            title = stringResource(R.string.setting_invert),
            description = stringResource(R.string.setting_invert_desc),
            checked = config.invertDirection,
            onCheckedChange = viewModel::setInvertDirection,
        )
        SwitchRow(
            title = stringResource(R.string.setting_haptics),
            checked = config.hapticFeedback,
            onCheckedChange = viewModel::setHapticFeedback,
        )
        SwitchRow(
            title = stringResource(R.string.setting_phone_keys),
            description = stringResource(R.string.setting_phone_keys_desc),
            checked = config.phoneKeysAsDial,
            onCheckedChange = viewModel::setPhoneKeysAsDial,
        )
        SwitchRow(
            title = stringResource(R.string.setting_require_headset),
            description = stringResource(R.string.setting_require_headset_desc),
            checked = config.requireHeadset,
            onCheckedChange = viewModel::setRequireHeadset,
        )
        if (config.requireHeadset) {
            // Edited locally so typing is not disturbed by the round trip through DataStore.
            var filter by rememberSaveable { mutableStateOf(config.headsetNameFilter) }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = {
                        filter = it
                        viewModel.setHeadsetNameFilter(it)
                    },
                    label = { Text(stringResource(R.string.setting_headset_filter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.engine.connectedHeadsets.isNotEmpty()) {
                    Text(stringResource(R.string.setting_headset_use), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.engine.connectedHeadsets.forEach { name ->
                            AssistChip(
                                onClick = {
                                    filter = name
                                    viewModel.setHeadsetNameFilter(name)
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        }
    }
}
