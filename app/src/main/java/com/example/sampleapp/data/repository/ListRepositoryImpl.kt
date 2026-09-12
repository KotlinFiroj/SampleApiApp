package com.example.sampleapp.data.repository

import com.example.sampleapp.data.mapper.toUserUI
import com.example.sampleapp.data.remote.ApiService
import com.example.sampleapp.domain.model.UserUI
import com.example.sampleapp.domain.repository.ListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class ListRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : ListRepository {

    // flow{} builder — cold Flow, only runs when collected (by the ViewModel).
    // Each emission is Result<T> so an error doesn't terminate the stream —
    // the ViewModel receives it and shows UiState.Error without crashing the pipeline.
    //
    // Production extension: add Room here as emit #1 (cached data) before the
    // network call so the user sees data instantly even when offline.
    override fun getUsers(): Flow<Result<List<UserUI>>> = flow {
        val response = apiService.getUserList()
        if (response.isSuccessful) {
            val users = response.body()?.map { it.toUserUI() } ?: emptyList()
            emit(Result.success(users))
        } else {
            emit(Result.failure(HttpException(response)))
        }
    }.catch { e ->
        // .catch only handles IOException and unexpected throwables.
        // CancellationException is NOT passed to catch — Flow propagates it
        // transparently, preserving structured concurrency.
        when (e) {
            is IOException   -> emit(Result.failure(e))
            is HttpException -> emit(Result.failure(e))
            else             -> emit(Result.failure(e))
        }
    }
}
