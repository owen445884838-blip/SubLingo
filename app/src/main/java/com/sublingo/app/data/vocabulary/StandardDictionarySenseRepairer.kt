package com.sublingo.app.data.vocabulary

import com.sublingo.app.data.db.LexemeEntity
import com.sublingo.app.data.db.LexemeSenseEntity
import com.sublingo.app.data.db.VocabularyDao
import com.sublingo.app.data.remote.DictionaryClient
import com.sublingo.app.data.remote.DictionaryEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val STANDARD_DICTIONARY_SOURCE = "standard-en-zh-v2"

@Singleton
class StandardDictionarySenseRepairer @Inject constructor(
    private val dictionary: DictionaryClient,
    private val vocabularyDao: VocabularyDao,
) {
    private val mutex = Mutex()

    suspend fun repairOutdatedSenses(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            var repaired = 0
            vocabularyDao.lexemesNeedingStandardSense(STANDARD_DICTIONARY_SOURCE).forEach { lexeme ->
                if (refreshLexeme(lexeme, allowRemote = false)) repaired++
            }
            repaired
        }
    }

    suspend fun refreshLexeme(lexeme: LexemeEntity, allowRemote: Boolean = true): Boolean {
        val entry = dictionary.lookup(lexeme.normalizedLemma, allowRemote) ?: return false
        return refreshLexeme(lexeme, entry)
    }

    suspend fun refreshLexeme(lexeme: LexemeEntity, entry: DictionaryEntry): Boolean {
        if (entry.senses.isEmpty()) return false
        vocabularyDao.upsertLexeme(
            lexeme.copy(
                phonetic = entry.phonetic ?: lexeme.phonetic,
                audioUrl = entry.audioUrl ?: lexeme.audioUrl,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        vocabularyDao.deleteNonUserSenses(lexeme.id)
        vocabularyDao.upsertSenses(
            entry.senses.mapIndexed { index, sense ->
                LexemeSenseEntity(
                    id = "${lexeme.id}-standard-$index",
                    lexemeId = lexeme.id,
                    pos = sense.pos,
                    definitionEn = sense.definition,
                    definitionZh = sense.definitionZh,
                    source = if (sense.definitionZh != null) STANDARD_DICTIONARY_SOURCE else "dictionaryapi.dev",
                )
            },
        )
        return true
    }
}
