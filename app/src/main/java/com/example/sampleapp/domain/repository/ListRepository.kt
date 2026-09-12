package com.example.sampleapp.domain.repository

import com.example.sampleapp.domain.model.UserUI
import kotlinx.coroutines.flow.Flow

// Returns Flow<Result<List<UserUI>>> — production pattern for offline-first:
//   emit 1 → cached data from local DB (instant, even when offline)
//   emit 2 → fresh data after network refresh
// Each emission is wrapped in Result so errors don't terminate the Flow.
// The ViewModel maps each emission to UiState.
interface ListRepository {
    fun getUsers(): Flow<Result<List<UserUI>>>
}
