package com.dialpilot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dialpilot.R
import com.dialpilot.core.action.ActionSetting
import com.dialpilot.core.action.DialAction
import kotlin.math.roundToInt

/**
 * Renders the [DialAction.settings] schema of one action. New actions get a settings UI for free.
 */
@Composable
fun ActionSettingsCard(
    action: DialAction,
    stored: Map<String, Any>,
    onChange: (key: String, value: Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(action.id) { mutableStateOf(false) }
    ElevatedCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        ClickableRow(
            title = stringResource(action.titleRes),
            onClick = { expanded = !expanded },
            trailing = {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                )
            },
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                action.settings.forEach { setting ->
                    when (setting) {
                        is ActionSetting.Slider -> SliderSetting(setting, stored[setting.key] as? Int ?: setting.default) {
                            onChange(setting.key, it)
                        }
                        is ActionSetting.Toggle -> SwitchRow(
                            title = stringResource(setting.titleRes),
                            checked = stored[setting.key] as? Boolean ?: setting.default,
                            onCheckedChange = { onChange(setting.key, it) },
                        )
                        is ActionSetting.Choice -> ChoiceSetting(setting, stored[setting.key] as? String ?: setting.default) {
                            onChange(setting.key, it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(setting: ActionSetting.Slider, value: Int, onChange: (Int) -> Unit) {
    // Local state while dragging; persisted once the finger lifts.
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val stepCount = ((setting.range.last - setting.range.first) / setting.step - 1).coerceAtLeast(0)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(stringResource(setting.titleRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${current.roundToInt()}${setting.unit}", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current.roundToInt()) },
            valueRange = setting.range.first.toFloat()..setting.range.last.toFloat(),
            steps = stepCount,
        )
    }
}

@Composable
private fun ChoiceSetting(setting: ActionSetting.Choice, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(setting.titleRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            setting.options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.value == value,
                    onClick = { onChange(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index, setting.options.size),
                ) { Text(stringResource(option.labelRes)) }
            }
        }
    }
}
