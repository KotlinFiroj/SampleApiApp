package com.example.sampleapp.data.mapper

import com.example.sampleapp.data.dto.ListItem
import com.example.sampleapp.domain.model.UserUI

// Extension function — stateless pure function, trivially unit-testable.
// Null defaults here keep UserUI non-nullable everywhere else.
fun ListItem.toUserUI() = UserUI(
    id       = id       ?: 0,
    name     = name     ?: "",
    username = username ?: "",
    email    = email    ?: "",
    phone    = phone    ?: "",
    website  = website  ?: "",
)
