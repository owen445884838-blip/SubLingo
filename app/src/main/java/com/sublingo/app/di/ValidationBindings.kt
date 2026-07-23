package com.sublingo.app.di

import com.sublingo.app.data.remote.InMemoryValidationLogProvider
import com.sublingo.app.data.validation.FakeAsrProbeProvider
import com.sublingo.app.data.validation.FakeDownloadProbeProvider
import com.sublingo.app.data.validation.FakeSubtitleProbeProvider
import com.sublingo.app.data.validation.FakeTranslationAlignmentProbeProvider
import com.sublingo.app.data.validation.ValidationStateHolder
import com.sublingo.app.domain.provider.AsrProbeProvider
import com.sublingo.app.domain.provider.DownloadProbeProvider
import com.sublingo.app.domain.provider.SubtitleProbeProvider
import com.sublingo.app.domain.provider.TranslationAlignmentProbeProvider
import com.sublingo.app.domain.provider.ValidationLogProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.Provides

@Module
@InstallIn(SingletonComponent::class)
abstract class ValidationBindings {
    @Binds abstract fun bindDownloadProbeProvider(impl: FakeDownloadProbeProvider): DownloadProbeProvider
    @Binds abstract fun bindSubtitleProbeProvider(impl: FakeSubtitleProbeProvider): SubtitleProbeProvider
    @Binds abstract fun bindAsrProbeProvider(impl: FakeAsrProbeProvider): AsrProbeProvider
    @Binds abstract fun bindTranslationAlignmentProbeProvider(impl: FakeTranslationAlignmentProbeProvider): TranslationAlignmentProbeProvider

    companion object {
        @Provides
        @Singleton
        fun provideValidationStateHolder(): ValidationStateHolder = ValidationStateHolder()

        @Provides
        @Singleton
        fun provideValidationLogProvider(): ValidationLogProvider = InMemoryValidationLogProvider()
    }
}
