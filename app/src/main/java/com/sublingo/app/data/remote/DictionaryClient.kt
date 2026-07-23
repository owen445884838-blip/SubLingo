package com.sublingo.app.data.remote

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.sublingo.app.data.db.DictionaryCacheDao
import com.sublingo.app.data.db.DictionaryCacheEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class DictionarySense(val pos: String?, val definition: String, val definitionZh: String? = null)
data class DictionaryEntry(val phonetic: String?, val audioUrl: String?, val senses: List<DictionarySense>)

internal fun standardDictionarySense(pos: String?, definitionEn: String, definitionZh: String): DictionarySense {
    val normalizedPos = pos?.trim()?.takeIf(String::isNotBlank) ?: inferPartOfSpeech("$definitionZh；$definitionEn")
    return DictionarySense(normalizedPos, definitionEn, definitionZh)
}

internal fun inferPartOfSpeech(definition: String): String? {
    val matches = Regex("(?i)(?:^|[^a-z])(vt|vi|v|n|adj|adv|a|prep|pron|conj|num|art|s)\\.")
        .findAll(definition)
        .map { match ->
            when (match.groupValues[1].lowercase()) {
                "vt", "vi", "v" -> "verb"
                "n" -> "noun"
                "adj", "a", "s" -> "adjective"
                "adv" -> "adverb"
                "prep" -> "preposition"
                "pron" -> "pronoun"
                "conj" -> "conjunction"
                "num" -> "number"
                "art" -> "article"
                else -> match.groupValues[1].lowercase()
            }
        }
        .distinct()
        .take(2)
        .toList()
    return matches.takeIf(List<String>::isNotEmpty)?.joinToString(" / ")
}

class DictionaryClient @Inject constructor(
    @ApplicationContext context: Context,
    private val http: OkHttpClient,
    private val cacheDao: DictionaryCacheDao,
    private val offlineDictionary: OfflineDictionaryPackManager,
) {
    private val localDictionary = BundledEnglishChineseDictionary(context)

    suspend fun lookup(query: String, allowRemote: Boolean = true): DictionaryEntry? {
        val normalized = query.lowercase().trim()
        offlineDictionary.lookup(normalized)?.let { return it }
        localDictionary.lookup(normalized)?.let { return it }
        // A missing entry may use the remote fallback, but a broken/unavailable
        // bundled database must not turn a whole transcript into hundreds of
        // sequential API calls.
        if (!localDictionary.isAvailable()) return null
        // Phrases receive their contextual meaning from the whole-transcript LLM response.
        // Dictionary API is reserved for single uncommon words/proper names missing locally.
        if (!allowRemote || normalized.any { it.isWhitespace() }) return null
        val cached = cacheDao.get(normalized)
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.responseJson?.let(::parse)
        }
        return runCatching {
            val url = "https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(normalized, "UTF-8")
            val raw = http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.code == 404) return@use null
                check(response.isSuccessful) { "词典请求失败 HTTP ${response.code}" }
                response.body?.string().orEmpty()
            }
            val entry = raw?.let(::parse)
            cacheDao.upsert(DictionaryCacheEntity(normalized, raw, if (entry == null) "NOT_FOUND" else "SUCCEEDED", null, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)))
            entry
        }.getOrElse { error ->
            cacheDao.upsert(DictionaryCacheEntity(normalized, null, "FAILED", error.message, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6)))
            null
        }
    }

    private fun parse(raw: String): DictionaryEntry? = runCatching {
        val first = Json.parseToJsonElement(raw).jsonArray.first().jsonObject
        val phonetics = first["phonetics"]?.jsonArray.orEmpty()
        val phonetic = first["phonetic"]?.jsonPrimitive?.contentOrNull
            ?: phonetics.firstNotNullOfOrNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        val audio = phonetics.firstNotNullOfOrNull { item ->
            item.jsonObject["audio"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        }?.let { if (it.startsWith("//")) "https:$it" else it }
        val senses = first["meanings"]?.jsonArray.orEmpty().flatMap { meaning ->
            val obj = meaning.jsonObject
            val pos = obj["partOfSpeech"]?.jsonPrimitive?.contentOrNull
            obj["definitions"]?.jsonArray.orEmpty().take(2).mapNotNull { definition ->
                definition.jsonObject["definition"]?.jsonPrimitive?.contentOrNull?.let { DictionarySense(pos, it) }
            }
        }.take(5)
        DictionaryEntry(phonetic, audio, senses)
    }.getOrNull()
}

private class BundledEnglishChineseDictionary(private val context: Context) {
    private val lock = Any()
    private val databaseFile get() = File(context.noBackupFilesDir, "dictionary/basic_en_zh.sqlite")

    fun lookup(query: String): DictionaryEntry? {
        val database = ensureInstalled() ?: run {
            Log.e(TAG, "Bundled dictionary is unavailable; local lookup skipped for '$query'")
            return null
        }
        return runCatching {
            SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
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
        }.onFailure { error ->
            Log.e(TAG, "Bundled dictionary lookup failed for '$query' at ${database.absolutePath}", error)
        }.getOrNull()
    }

    fun isAvailable(): Boolean = ensureInstalled() != null

    private fun ensureInstalled(): File? = synchronized(lock) {
        if (databaseFile.isFile && databaseFile.length() > MIN_DATABASE_BYTES && hasCurrentFormat(databaseFile)) {
            return@synchronized databaseFile
        }
        val pending = File(databaseFile.absolutePath + ".part")
        runCatching {
            databaseFile.parentFile?.mkdirs()
            pending.delete()
            context.assets.open(BUNDLED_ASSET).use { source ->
                GZIPInputStream(source).use { gzip ->
                    pending.outputStream().buffered().use { output -> gzip.copyTo(output) }
                }
            }
            check(pending.length() > MIN_DATABASE_BYTES) { "内置基础词典解压结果异常" }
            databaseFile.delete()
            check(pending.renameTo(databaseFile)) { "无法提交内置基础词典" }
            databaseFile
        }.getOrElse { error ->
            Log.e(TAG, "Unable to install bundled dictionary asset '$BUNDLED_ASSET'", error)
            pending.delete()
            null
        }
    }

    private fun hasCurrentFormat(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM metadata WHERE key = 'format_version' LIMIT 1", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == BASIC_DICTIONARY_FORMAT_VERSION
            }
        }
    }.getOrDefault(false)

    private companion object {
        const val MIN_DATABASE_BYTES = 1_000_000L
        const val BUNDLED_ASSET = "dictionary/basic_en_zh.sqlite.pack"
        const val TAG = "BundledDictionary"
        // Version 3 forces devices that extracted an earlier 45k selection under
        // format 2 to install the current asset. Some common entries (for example
        // "tv") were absent from that old selection even though they exist now.
        const val BASIC_DICTIONARY_FORMAT_VERSION = "3"
    }
}
