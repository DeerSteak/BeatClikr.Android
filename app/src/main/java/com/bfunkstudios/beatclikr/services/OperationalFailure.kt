package com.bfunkstudios.beatclikr.services

enum class FailureDomain {
    ASSET_VALIDATION,
    SOUND_DECODE_CACHE,
    AUDIO_STREAM,
    AUDIO_ROUTE,
    DATABASE,
    REMINDER,
    HAPTIC,
    TORCH,
    SCHEDULER
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
