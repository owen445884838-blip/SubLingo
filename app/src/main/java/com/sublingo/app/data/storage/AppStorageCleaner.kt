package com.sublingo.app.data.storage

import android.content.Context
import android.util.Log
import com.sublingo.app.data.db.AudioChunkDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStorageCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioChunkDao: AudioChunkDao,
) {
    fun cleanStartupTransients(now: Long = System.currentTimeMillis()) {
        deleteChildren(File(context.cacheDir, "download-cookies"))
        cleanOldPartFiles(File(context.getExternalFilesDir(null), "imports"), now)
        cleanOldPartFiles(File(context.getExternalFilesDir(null), "downloads"), now)
        cleanOldPartFiles(File(context.noBackupFilesDir, "dictionary"), now)
    }

    suspend fun cleanCompletedPipeline(jobId: String) {
        val audioDirectory = File(context.getExternalFilesDir(null), "audio/$jobId")
        deleteTree(audioDirectory)
        audioChunkDao.deleteByJobId(jobId)
    }

    private fun cleanOldPartFiles(root: File, now: Long) {
        if (!root.isDirectory) return
        root.walkBottomUp()
            .filter { it.isFile && StorageCleanupPolicy.shouldDeletePartFile(it.name, it.lastModified(), now) }
            .forEach(::deleteFile)
    }

    private fun deleteChildren(directory: File) {
        directory.listFiles().orEmpty().forEach { child ->
            if (child.isDirectory) deleteTree(child) else deleteFile(child)
        }
    }

    private fun deleteTree(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            Log.w(TAG, "Unable to remove transient directory ${directory.name}")
        }
    }

    private fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "Unable to remove transient file ${file.name}")
    }

    private companion object { const val TAG = "AppStorageCleaner" }
}

object StorageCleanupPolicy {
    const val PART_FILE_RETENTION_MS = 24L * 60L * 60L * 1_000L

    fun shouldDeletePartFile(name: String, lastModified: Long, now: Long): Boolean =
        name.contains(".part") && lastModified > 0L && now - lastModified >= PART_FILE_RETENTION_MS
}
