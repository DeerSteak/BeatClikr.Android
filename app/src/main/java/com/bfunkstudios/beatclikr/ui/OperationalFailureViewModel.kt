package com.bfunkstudios.beatclikr.ui

import androidx.lifecycle.ViewModel
import com.bfunkstudios.beatclikr.services.OperationalFailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OperationalFailureViewModel @Inject constructor(
    private val reporter: OperationalFailureReporter
) : ViewModel() {
    val failure = reporter.failure

    fun dismiss() = reporter.clear()
}
