package com.example.sampleapp.data.mapper

import com.example.sampleapp.data.dto.ListItem
import com.example.sampleapp.domain.model.UserUI

fun ListItem.toUser(): UserUI {
    return UserUI(name = name ?: "")
}
