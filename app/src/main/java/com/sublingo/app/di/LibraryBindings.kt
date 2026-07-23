package com.sublingo.app.di

import com.sublingo.app.data.library.RoomLibraryProvider
import com.sublingo.app.domain.provider.LibraryProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryBindings {
    @Binds abstract fun bindLibraryProvider(impl: RoomLibraryProvider): LibraryProvider
}
