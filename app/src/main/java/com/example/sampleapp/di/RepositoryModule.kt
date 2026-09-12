package com.example.sampleapp.di

import com.example.sampleapp.data.repository.ListRepositoryImpl
import com.example.sampleapp.domain.repository.ListRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // @Binds — compile-time zero-overhead binding (no function call at runtime)
    // More efficient than @Provides for binding interface → implementation
    @Binds @Singleton
    abstract fun bindListRepository(impl: ListRepositoryImpl): ListRepository
}
