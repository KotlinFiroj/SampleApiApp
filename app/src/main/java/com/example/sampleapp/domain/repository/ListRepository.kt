package com.example.sampleapp.domain.repository

import com.example.sampleapp.prasentation.view.UiState
import kotlinx.coroutines.flow.Flow

interface ListRepository {

    suspend fun getUserList(): Flow<UiState>
}
