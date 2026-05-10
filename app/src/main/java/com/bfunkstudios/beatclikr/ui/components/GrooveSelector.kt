package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.data.Groove

@Composable
fun GrooveSelector(
    selected: Groove,
    onSelect: (Groove) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Groove.standardEntries.chunked(2).forEach { rowGrooves ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowGrooves.forEach { groove ->
                    GrooveButton(
                        groove = groove,
                        isSelected = selected == groove,
                        onClick = { onSelect(groove) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
