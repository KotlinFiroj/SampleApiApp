package com.example.sampleapp.prasentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sampleapp.domain.model.UserUI
import com.example.sampleapp.domain.usecase.ListUseCause
import com.example.sampleapp.prasentation.view.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    private val useCase: ListUseCause,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<UserUI>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<UserUI>>> = _uiState.asStateFlow()

    init { loadUsers() }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // collect every emission from the Flow — each one is a Result<List<UserUI>>
            // In offline-first: emit 1 = cached DB data, emit 2 = fresh network data
            useCase().collect { result ->
                result.fold(
                    onSuccess = { users ->
                        _uiState.value = if (users.isEmpty()) UiState.Empty
                                         else UiState.Success(users)
                    },
                    onFailure = { _uiState.value = UiState.Error(it.toMessage()) }
                )
            }
        }
    }
}

private fun Throwable.toMessage(): String = when (this) {
    is IOException   -> "Network error. Please check your connection."
    is HttpException -> "Server error (${code()}). Please try again."
    else             -> message ?: "An unexpected error occurred."
}
