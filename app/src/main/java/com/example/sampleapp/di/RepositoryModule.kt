package com.example.sampleapp.di

import com.example.sampleapp.data.remote.ApiService
import com.example.sampleapp.data.repository.ListRepositoryImpl
import com.example.sampleapp.domain.repository.ListRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    fun provideListRepository(apiService: ApiService): ListRepository {
        return ListRepositoryImpl(apiService)
    }
}
