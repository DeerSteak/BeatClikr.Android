package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.Subdivisions

@Composable
fun GrooveButton(
    subdivision: Subdivisions,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondary
                             else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary
                           else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = when (subdivision) {
                Subdivisions.Quarter   -> stringResource(R.string.subdivision_quarter)
                Subdivisions.Eighth    -> stringResource(R.string.subdivision_eighth)
                Subdivisions.Triplet   -> stringResource(R.string.subdivision_triplet)
                Subdivisions.Sixteenth -> stringResource(R.string.subdivision_sixteenth)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
