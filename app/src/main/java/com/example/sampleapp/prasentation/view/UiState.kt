package com.example.sampleapp.prasentation.view

import com.example.sampleapp.domain.model.UserUI

sealed class UiState {

    object Idle : UiState()
    object Loading : UiState()
    data class Success(val list: List<UserUI>) : UiState()
    data class Failure(val message: String) : UiState()
}
