package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.ui.formatBpm
import kotlin.math.roundToInt

@Composable
fun BpmSliderControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val label = stringResource(R.string.bpm)
    val decreaseLabel = stringResource(R.string.decrease_value, label)
    val increaseLabel = stringResource(R.string.increase_value, label)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedIconButton(
            onClick = { onValueChange((value - 1f).coerceIn(valueRange)) },
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseLabel },
            colors = IconButtonDefaults.outlinedIconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = null)
        }

        Slider(
            value = value,
            onValueChange = { onValueChange(it.roundToInt().toFloat()) },
            valueRange = valueRange,
            steps = (valueRange.endInclusive - valueRange.start).roundToInt() - 1,
            enabled = enabled,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = label
                stateDescription = formatBpm(value)
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            )
        )

        OutlinedIconButton(
            onClick = { onValueChange((value + 1f).coerceIn(valueRange)) },
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = increaseLabel },
            colors = IconButtonDefaults.outlinedIconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}
