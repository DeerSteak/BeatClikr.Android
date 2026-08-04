package com.bfunkstudios.beatclikr.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class FailureDomain {
    ASSET_VALIDATION,
    SOUND_DECODE_CACHE,
    AUDIO_STREAM,
    AUDIO_ROUTE,
    DATABASE,
    REMINDER,
    HAPTIC,
    TORCH,
    SCHEDULER,
    FOREGROUND_SERVICE
}

enum class FailureDisposition {
    RETRYABLE,
    USER_ACTIONABLE,
    DEGRADED,
    FATAL
}

enum class FailureRecoveryAction {
    RETRY,
    CHECK_AUDIO_OUTPUT,
    OPEN_SETTINGS,
    DISABLE_EFFECT,
    RESTORE_LAST_GOOD_STATE,
    NONE
}

data class OperationalFailure(
    val domain: FailureDomain,
    val code: String,
    val disposition: FailureDisposition,
    val recoveryAction: FailureRecoveryAction
)

@Singleton
class OperationalFailureReporter @Inject constructor() {
    private val mutableFailure = MutableStateFlow<OperationalFailure?>(null)
    val failure = mutableFailure.asStateFlow()

    fun report(failure: OperationalFailure) {
        mutableFailure.value = failure
    }

    fun clear() {
        mutableFailure.value = null
    }
}

fun databaseFailure(code: String) = OperationalFailure(
    FailureDomain.DATABASE,
    code,
    FailureDisposition.RETRYABLE,
    FailureRecoveryAction.RETRY
)

fun reminderFailure(code: String) = OperationalFailure(
    FailureDomain.REMINDER,
    code,
    FailureDisposition.USER_ACTIONABLE,
    FailureRecoveryAction.OPEN_SETTINGS
)

fun CoroutineScope.launchReporting(
    reporter: OperationalFailureReporter,
    failure: OperationalFailure,
    action: suspend () -> Unit
) = launch {
    try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        reporter.report(failure)
    }
}
