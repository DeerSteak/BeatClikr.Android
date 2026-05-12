package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.layout.PaddingValues
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
import com.bfunkstudios.beatclikr.data.Groove

@Composable
fun GrooveButton(
    groove: Groove,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = when (groove) {
                Groove.Quarter -> stringResource(R.string.subdivision_quarter)
                Groove.Eighth -> stringResource(R.string.subdivision_eighth)
                Groove.Triplet -> stringResource(R.string.subdivision_triplet)
                Groove.Sixteenth -> stringResource(R.string.subdivision_sixteenth)
                Groove.OddMeterQuarter -> stringResource(R.string.groove_odd_quarter)
                Groove.OddMeterEighth -> stringResource(R.string.groove_odd_eighth)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
