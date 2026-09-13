package com.example.sampleapp.prasentation.viewModel

import android.util.Log
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
            // No try/catch needed here — Repository guarantees every error
            // is already wrapped in Result.failure(). The ViewModel only
            // needs to map Result → UiState, never handle raw exceptions.
            useCase().collect { result ->
                result.fold(
                    onSuccess = { users ->
                        _uiState.value = if (users.isEmpty()) UiState.Empty
                                         else UiState.Success(users)
                    },
                    onFailure = { throwable ->
                        Log.e(TAG, "loadUsers failed: ${throwable.message}")
                        _uiState.value = UiState.Error(throwable.toMessage())
                    }
                )
            }
            // CancellationException is NOT caught — coroutine cancels cleanly
        }
    }

    companion object { private const val TAG = "ListViewModel" }
}

private fun Throwable.toMessage(): String = when (this) {
    else -> message ?: "An unexpected error occurred."
}
