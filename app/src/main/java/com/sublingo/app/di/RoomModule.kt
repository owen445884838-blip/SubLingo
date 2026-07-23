package com.sublingo.app.di

import android.content.Context
import androidx.room.Room
import com.sublingo.app.data.db.ProcessingJobDao
import com.sublingo.app.data.db.SubLingoDatabase
import com.sublingo.app.data.db.VideoDao
import com.sublingo.app.data.db.SubtitleTrackDao
import com.sublingo.app.data.db.SubtitleCueDao
import com.sublingo.app.data.db.ProviderProfileDao
import com.sublingo.app.data.db.AudioChunkDao
import com.sublingo.app.data.db.TranslationBatchDao
import com.sublingo.app.data.db.VocabularyDao
import com.sublingo.app.data.db.DictionaryCacheDao
import com.sublingo.app.data.db.ReviewDao
import com.sublingo.app.data.db.ALL_MIGRATIONS
import com.sublingo.app.data.db.SubtitleWordAlignmentDao
import com.sublingo.app.data.db.VocabularyLlmBatchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SubLingoDatabase {
        return Room.databaseBuilder(
            context,
            SubLingoDatabase::class.java,
            "sublingo.db",
        ).addMigrations(*ALL_MIGRATIONS).build()
    }

    @Provides
    fun provideVideoDao(database: SubLingoDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideProcessingJobDao(database: SubLingoDatabase): ProcessingJobDao = database.processingJobDao()

    @Provides fun provideSubtitleTrackDao(database: SubLingoDatabase): SubtitleTrackDao = database.subtitleTrackDao()
    @Provides fun provideSubtitleCueDao(database: SubLingoDatabase): SubtitleCueDao = database.subtitleCueDao()
    @Provides fun provideSubtitleWordAlignmentDao(database: SubLingoDatabase): SubtitleWordAlignmentDao = database.subtitleWordAlignmentDao()
    @Provides fun provideProviderProfileDao(database: SubLingoDatabase): ProviderProfileDao = database.providerProfileDao()
    @Provides fun provideAudioChunkDao(database: SubLingoDatabase): AudioChunkDao = database.audioChunkDao()
    @Provides fun provideTranslationBatchDao(database: SubLingoDatabase): TranslationBatchDao = database.translationBatchDao()
    @Provides fun provideVocabularyLlmBatchDao(database: SubLingoDatabase): VocabularyLlmBatchDao = database.vocabularyLlmBatchDao()
    @Provides fun provideVocabularyDao(database: SubLingoDatabase): VocabularyDao = database.vocabularyDao()
    @Provides fun provideReviewDao(database: SubLingoDatabase): ReviewDao = database.reviewDao()
    @Provides fun provideDictionaryCacheDao(database: SubLingoDatabase): DictionaryCacheDao = database.dictionaryCacheDao()
}
