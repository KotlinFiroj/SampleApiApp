package com.example.sampleapp.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ListItem(
    @Json(name = "address")
    val address: Address? = null,
    @Json(name = "company")
    val company: Company? = null,
    @Json(name = "email")
    val email: String? = null,
    @Json(name = "id")
    val id: Int? = null,
    @Json(name = "name")
    val name: String? = null,
    @Json(name = "phone")
    val phone: String? = null,
    @Json(name = "username")
    val username: String? = null,
    @Json(name = "website")
    val website: String? = null,
)
