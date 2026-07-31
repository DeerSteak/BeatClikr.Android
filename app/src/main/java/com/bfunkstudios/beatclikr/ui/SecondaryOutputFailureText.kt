package com.bfunkstudios.beatclikr.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bfunkstudios.beatclikr.R

@Composable
internal fun SecondaryOutputFailureText(diagnostic: String?) {
    diagnostic ?: return
    Text(
        text = stringResource(R.string.secondary_output_failure, diagnostic),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}
