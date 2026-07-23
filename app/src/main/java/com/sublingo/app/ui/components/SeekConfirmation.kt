package com.sublingo.app.ui.components

import kotlin.math.abs

internal fun isSeekConfirmed(currentMs: Long, targetMs: Long, reportedPositionMs: Long?): Boolean =
    abs(currentMs - targetMs) <= 1_500L ||
        (reportedPositionMs != null && abs(reportedPositionMs - targetMs) <= 1_500L)

internal fun seekConfirmationTimedOut(nowMs: Long, startedAtMs: Long): Boolean =
    startedAtMs > 0L && nowMs - startedAtMs >= 2_000L
