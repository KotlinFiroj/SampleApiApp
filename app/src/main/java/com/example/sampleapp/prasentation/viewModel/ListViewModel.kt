package com.example.sampleapp.prasentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sampleapp.domain.usecase.ListUseCause
import com.example.sampleapp.prasentation.view.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(val listUseCause: ListUseCause) : ViewModel() {

    private val _list = MutableStateFlow<UiState>(UiState.Loading)
    val list = _list.asStateFlow()

    init {
        getUserList()
    }

    fun getUserList() {
        viewModelScope.launch {
            listUseCause.invoke()
                .catch { }
                .collect {
                    _list.value = it
                }
        }
    }
}
