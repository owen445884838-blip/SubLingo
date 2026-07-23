package com.sublingo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

abstract class BaseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params)
