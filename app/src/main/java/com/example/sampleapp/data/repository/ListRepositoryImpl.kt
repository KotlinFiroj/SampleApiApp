package com.example.sampleapp.data.repository

import com.example.sampleapp.data.mapper.toUser
import com.example.sampleapp.data.remote.ApiService
import com.example.sampleapp.domain.repository.ListRepository
import com.example.sampleapp.prasentation.view.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

class ListRepositoryImpl @Inject constructor(val apiService: ApiService) : ListRepository {

    override suspend fun getUserList(): Flow<UiState> = flow {
        try {
            emit(UiState.Loading)
            val response = apiService.getUserList()
            if (response.isSuccessful) {
                val data = response.body()
                val res = data?.let {
                    it.map { it.toUser() }
                    //  .sortedBy { it.name }.take(3)
                }
                emit(UiState.Success(res!!))
            } else {
                emit(UiState.Failure(""))
            }
        } catch (e: SocketTimeoutException) {
            emit(UiState.Failure(e.message ?: "Unknown Exception"))
        } catch (e: IOException) {
            emit(UiState.Failure(e.message ?: "Unknown Exception"))
        } catch (e: Exception) {
            emit(UiState.Failure(e.message ?: "Unknown Exception"))
        }
    }.flowOn(Dispatchers.IO)
}
