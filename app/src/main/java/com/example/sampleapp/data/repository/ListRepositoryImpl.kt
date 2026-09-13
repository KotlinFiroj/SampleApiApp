package com.example.sampleapp.data.repository

import android.util.Log
import com.example.sampleapp.data.mapper.toUserUI
import com.example.sampleapp.data.remote.ApiService
import com.example.sampleapp.domain.model.UserUI
import com.example.sampleapp.domain.repository.ListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import javax.inject.Inject

class ListRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : ListRepository {

    // try/catch belongs HERE — Repository is the data layer boundary.
    // Retrofit throws IOException / HttpException — catch them here and
    // convert to Result.failure() so nothing above this layer ever sees an exception.
    // .catch {} handles exceptions thrown inside flow{} — equivalent to try/catch
    // but keeps the Flow alive for future emissions (e.g. cache-then-network).
    override fun getUsers(): Flow<Result<List<UserUI>>> = flow {
        val response = apiService.getUserList()
        if (response.isSuccessful) {
            val users = response.body()?.map { it.toUserUI() } ?: emptyList()
            Log.d(TAG, "getUsers: ${users.size} users fetched")
            emit(Result.success(users))
        } else {
            emit(Result.failure(HttpException(response)))
        }
    }.catch { e ->
        // CancellationException never reaches here — Flow propagates it transparently.
        // Every other throwable (IOException, HttpException, etc.) becomes Result.failure.
        emit(Result.failure(e))
    }

    companion object { private const val TAG = "ListRepository" }
}
