package com.example.sampleapp.domain.usecase

import com.example.sampleapp.domain.model.UserUI
import com.example.sampleapp.domain.repository.ListRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ListUseCause @Inject constructor(
    private val repository: ListRepository,
) {
    operator fun invoke(): Flow<Result<List<UserUI>>> = repository.getUsers()
}
