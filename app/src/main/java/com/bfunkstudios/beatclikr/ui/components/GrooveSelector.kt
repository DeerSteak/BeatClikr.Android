package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.data.Subdivisions

@Composable
fun GrooveSelector(
    selected: Subdivisions,
    onSelect: (Subdivisions) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Subdivisions.entries.take(2).forEach { subdivision ->
                GrooveButton(
                    subdivision = subdivision,
                    isSelected = selected == subdivision,
                    onClick = { onSelect(subdivision) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Subdivisions.entries.drop(2).forEach { subdivision ->
                GrooveButton(
                    subdivision = subdivision,
                    isSelected = selected == subdivision,
                    onClick = { onSelect(subdivision) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
