package com.bfunkstudios.beatclikr.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.services.SecondaryOutput
import com.bfunkstudios.beatclikr.services.SecondaryOutputFailure

@Composable
internal fun SecondaryOutputFailureText(failure: SecondaryOutputFailure?) {
    failure ?: return
    val detail = when (failure.output) {
        SecondaryOutput.HAPTIC -> stringResource(R.string.haptic_unavailable)
        SecondaryOutput.TORCH -> stringResource(R.string.torch_unavailable)
        SecondaryOutput.SCHEDULER -> stringResource(R.string.secondary_scheduler_unavailable)
    }
    Text(
        text = stringResource(R.string.secondary_output_failure, detail),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}
