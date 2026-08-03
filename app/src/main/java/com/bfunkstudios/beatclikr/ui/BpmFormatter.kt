package com.bfunkstudios.beatclikr.ui

import java.text.NumberFormat
import java.util.Locale

fun formatBpm(value: Float, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(value.toDouble())
