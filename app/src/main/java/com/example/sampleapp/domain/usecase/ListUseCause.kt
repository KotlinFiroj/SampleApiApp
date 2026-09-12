package com.example.sampleapp.domain.usecase

import com.example.sampleapp.domain.model.UserUI
import com.example.sampleapp.domain.repository.ListRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// UseCase returns Flow<Result<List<UserUI>>> — passes the repository stream through.
// Right place to add business logic: filter, sort, combine multiple repos.
// operator fun invoke() — called as useCase() in the ViewModel, idiomatic Kotlin.
class ListUseCause @Inject constructor(
    private val repository: ListRepository,
) {
    operator fun invoke(): Flow<Result<List<UserUI>>> = repository.getUsers()
}
