package com.example.sampleapp.data.mapper

import com.example.sampleapp.data.dto.ListItem
import com.example.sampleapp.domain.model.UserUI

fun ListItem.toUserUI() = UserUI(
    id       = id       ?: 0,
    name     = name     ?: "",
    username = username ?: "",
    email    = email    ?: "",
    phone    = phone    ?: "",
    website  = website  ?: "",
)
