package com.example.sampleapp.domain.usecase

import com.example.sampleapp.domain.repository.ListRepository
import com.example.sampleapp.prasentation.view.UiState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ListUseCause @Inject constructor(val listRepositoryImpl: ListRepository) {

    suspend operator fun invoke(): Flow<UiState> {
        return listRepositoryImpl.getUserList()
    }
}
