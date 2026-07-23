package com.sublingo.app.data.remote

import android.content.ContentValues
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class OfflineDictionaryState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val entryCount: Int = 0,
    val sizeBytes: Long = 0L,
    val version: String = "ECDICT",
    val status: String = "未下载完整离线词典",
)

@Singleton
class OfflineDictionaryPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
) {
    private val preferences = context.getSharedPreferences("offline_dictionary", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<OfflineDictionaryState> = _state.asStateFlow()

    val databaseFile: File get() = File(context.noBackupFilesDir, "dictionary/ecdict.sqlite")
    val temporaryDatabaseFile: File get() = File(context.noBackupFilesDir, "dictionary/ecdict.sqlite.part")

    fun download() {
        update(downloading = true, progress = 0, status = "正在准备下载完整离线词典")
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OfflineDictionaryDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun delete() {
        workManager.cancelUniqueWork(WORK_NAME)
        databaseFile.delete()
        temporaryDatabaseFile.delete()
        preferences.edit().clear().apply()
        _state.value = readState()
    }

    fun lookup(query: String): DictionaryEntry? {
        val file = databaseFile.takeIf(File::isFile) ?: return null
        return runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use database@{ db ->
                db.query(
                    "entries",
                    arrayOf("phonetic", "pos", "definition_en", "definition_zh"),
                    "word = ?",
                    arrayOf(query.lowercase()),
                    null,
                    null,
                    null,
                    "1",
                ).use cursor@{ cursor ->
                    if (!cursor.moveToFirst()) return@cursor null
                    val phonetic = cursor.getString(0)?.takeIf(String::isNotBlank)
                    val pos = cursor.getString(1)?.takeIf(String::isNotBlank)
                    val definitionEn = cursor.getString(2)?.takeIf(String::isNotBlank) ?: query
                    val definitionZh = cursor.getString(3)?.takeIf(String::isNotBlank) ?: return@cursor null
                    DictionaryEntry(phonetic, null, listOf(standardDictionarySense(pos, definitionEn, definitionZh)))
                }
            }
        }.onFailure { Log.w(TAG, "Offline dictionary lookup failed", it) }.getOrNull()
    }

    internal fun update(
        installed: Boolean = _state.value.installed,
        downloading: Boolean = _state.value.downloading,
        progress: Int = _state.value.progress,
        entryCount: Int = _state.value.entryCount,
        sizeBytes: Long = databaseFile.takeIf(File::exists)?.length() ?: _state.value.sizeBytes,
        status: String = _state.value.status,
    ) {
        preferences.edit()
            .putBoolean("installed", installed)
            .putInt("entryCount", entryCount)
            .putLong("sizeBytes", sizeBytes)
            .putString("status", status)
            .apply()
        _state.value = OfflineDictionaryState(installed, downloading, progress, entryCount, sizeBytes, status = status)
    }

    private fun readState(): OfflineDictionaryState {
        val installed = databaseFile.isFile && preferences.getBoolean("installed", false)
        return OfflineDictionaryState(
            installed = installed,
            entryCount = if (installed) preferences.getInt("entryCount", 0) else 0,
            sizeBytes = if (installed) databaseFile.length() else 0L,
            status = if (installed) preferences.getString("status", null) ?: "完整离线词典已安装" else "未下载完整离线词典",
        )
    }

    companion object {
        const val SOURCE_URL = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
        const val WORK_NAME = "offline-dictionary-ecdict"
        private const val TAG = "OfflineDictionary"
    }
}

@HiltWorker
class OfflineDictionaryDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val http: OkHttpClient,
    private val manager: OfflineDictionaryPackManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(createForegroundInfo("正在准备离线词典"))
        val target = manager.databaseFile
        val temporary = manager.temporaryDatabaseFile
        target.parentFile?.mkdirs()
        temporary.delete()
        try {
            manager.update(downloading = true, progress = 1, status = "正在下载 ECDICT 数据")
            val request = Request.Builder()
                .url(OfflineDictionaryPackManager.SOURCE_URL)
                .header("User-Agent", "SubLingo-Offline-Dictionary")
                .get()
                .build()
            val downloadClient = http.newBuilder()
                .callTimeout(30, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build()
            val count = downloadClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "词典下载失败 HTTP ${response.code}" }
                val body = requireNotNull(response.body) { "词典下载响应为空" }
                val progressStream = ProgressInputStream(body.byteStream(), body.contentLength()) { bytesRead, total ->
                    val progress = if (total > 0L) (bytesRead * 45L / total).toInt().coerceIn(1, 45) else 1
                    val status = "正在下载词典：${formatBytes(bytesRead)}"
                    manager.update(downloading = true, progress = progress, status = status)
                    setProgressAsync(androidx.work.workDataOf("progress" to progress, "status" to status))
                }
                SQLiteDatabase.openOrCreateDatabase(temporary, null).use { db ->
                    createSchema(db)
                    BufferedReader(InputStreamReader(progressStream, Charsets.UTF_8), 128 * 1024).use { reader ->
                        importCsv(reader, db)
                    }
                }
            }
            check(count > 10_000) { "词典数据不完整，仅导入 $count 条" }
            check(temporary.length() > 1_000_000L) { "词典数据库文件异常" }
            target.delete()
            check(temporary.renameTo(target)) { "无法提交词典数据库" }
            manager.update(
                installed = true,
                downloading = false,
                progress = 100,
                entryCount = count,
                sizeBytes = target.length(),
                status = "完整离线词典已安装",
            )
            Result.success()
        } catch (error: Throwable) {
            Log.e("OfflineDictionary", "Dictionary installation failed", error)
            temporary.delete()
            manager.update(downloading = false, progress = 0, status = "词典下载失败：${error.message ?: "未知错误"}")
            Result.failure()
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("PRAGMA journal_mode=OFF")
        db.execSQL("PRAGMA synchronous=OFF")
        db.execSQL(
            "CREATE TABLE entries (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, pos TEXT, definition_en TEXT, definition_zh TEXT)",
        )
    }

    private fun importCsv(reader: BufferedReader, db: SQLiteDatabase): Int {
        val csv = CsvRecordReader(reader)
        val header = csv.next() ?: return 0
        val indexes = header.withIndex().associate { it.value.trim().lowercase() to it.index }
        fun List<String>.value(name: String): String = indexes[name]?.let { getOrNull(it) }.orEmpty().trim()
        var count = 0
        db.beginTransaction()
        try {
            while (true) {
                if (isStopped) error("词典下载已取消")
                val row = csv.next() ?: break
                val word = row.value("word").lowercase()
                val translation = row.value("translation")
                if (word.isBlank() || translation.isBlank()) continue
                val values = ContentValues().apply {
                    put("word", word)
                    put("phonetic", row.value("phonetic"))
                    put("pos", row.value("pos"))
                    put("definition_en", row.value("definition"))
                    put("definition_zh", translation.replace("\\n", "；"))
                }
                db.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                count++
                if (count % 5_000 == 0) {
                    val progress = (45 + count / 4_000).coerceAtMost(95)
                    val status = "正在建立离线索引：$count 条"
                    manager.update(downloading = true, progress = progress, status = status)
                    setProgressAsync(androidx.work.workDataOf("progress" to progress, "status" to status))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("正在安装完整离线词典")

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "离线词典下载", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SubLingo 完整离线词典")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, managerStateProgress(), false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun managerStateProgress(): Int = manager.state.value.progress.coerceIn(0, 100)

    private companion object {
        const val CHANNEL_ID = "offline_dictionary"
        const val NOTIFICATION_ID = 4102
    }
}

private class ProgressInputStream(
    source: InputStream,
    private val totalBytes: Long,
    private val onProgress: (Long, Long) -> Unit,
) : FilterInputStream(source) {
    private var bytesRead = 0L
    private var lastReported = 0L

    override fun read(): Int = super.read().also { if (it >= 0) record(1) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { if (it > 0) record(it.toLong()) }

    private fun record(count: Long) {
        bytesRead += count
        if (bytesRead - lastReported >= 512 * 1024 || bytesRead == totalBytes) {
            lastReported = bytesRead
            onProgress(bytesRead, totalBytes)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private class CsvRecordReader(private val reader: BufferedReader) {
    fun next(): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var readAny = false
        while (true) {
            val value = reader.read()
            if (value == -1) {
                if (!readAny && field.isEmpty() && fields.isEmpty()) return null
                fields += field.toString()
                return fields
            }
            readAny = true
            val char = value.toChar()
            when {
                char == '"' && quoted -> {
                    reader.mark(1)
                    if (reader.read() == '"'.code) field.append('"') else {
                        reader.reset()
                        quoted = false
                    }
                }
                char == '"' && field.isEmpty() -> quoted = true
                char == ',' && !quoted -> { fields += field.toString(); field.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r') {
                        reader.mark(1)
                        if (reader.read() != '\n'.code) reader.reset()
                    }
                    fields += field.toString()
                    return fields
                }
                else -> field.append(char)
            }
        }
    }
}
