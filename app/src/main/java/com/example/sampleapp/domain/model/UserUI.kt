package com.example.sampleapp.domain.model

// Domain model — all fields non-nullable. Null-safety is resolved once at the mapper boundary.
// Decoupled from the network DTO so API contract changes don't ripple into the UI.
data class UserUI(
    val id: Int,
    val name: String,
    val username: String,
    val email: String,
    val phone: String,
    val website: String,
)
