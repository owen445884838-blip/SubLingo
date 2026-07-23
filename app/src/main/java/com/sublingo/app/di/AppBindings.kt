package com.sublingo.app.di

import com.sublingo.app.data.repository.RoomProcessingJobRepository
import com.sublingo.app.data.repository.RoomVideoRepository
import com.sublingo.app.domain.repository.ProcessingJobRepository
import com.sublingo.app.domain.repository.VideoRepository
import com.sublingo.app.security.AndroidSecretStore
import com.sublingo.app.security.SecretStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {

    @Binds
    abstract fun bindVideoRepository(impl: RoomVideoRepository): VideoRepository

    @Binds
    abstract fun bindProcessingJobRepository(impl: RoomProcessingJobRepository): ProcessingJobRepository

    @Binds
    abstract fun bindSecretStore(impl: AndroidSecretStore): SecretStore
}
