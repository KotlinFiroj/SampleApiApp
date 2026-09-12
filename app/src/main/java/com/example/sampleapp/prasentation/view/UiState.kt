package com.example.sampleapp.prasentation.view

// sealed interface (not sealed class) — allows a class to implement multiple sealed types.
// <out T> covariant — UiState<User> is a subtype of UiState<Any>.
// data object — structural equality + toString for free, correct for singletons.
// Empty: HTTP 200 + empty list ≠ error. Differentiate them so the UI shows the right message.
sealed interface UiState<out T> {
    data object Loading          : UiState<Nothing>
    data class  Success<T>(val data: T) : UiState<T>
    data object Empty            : UiState<Nothing>
    data class  Error(val message: String) : UiState<Nothing>
}
