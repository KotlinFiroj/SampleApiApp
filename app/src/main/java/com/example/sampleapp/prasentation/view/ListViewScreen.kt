package com.example.sampleapp.prasentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sampleapp.prasentation.viewModel.ListViewModel

@Composable
fun LoadUserList(viewModel: ListViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsState()
    when (list) {
        is UiState.Loading -> {
        }
        is UiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                /*items((list as UiState.Success).list) { item ->
                    Text(item.name)
                }*/
            }
        }
        is UiState.Failure -> {
        }

        UiState.Idle -> {
        }
    }
}
