package com.example.sampleapp.data.remote

import com.example.sampleapp.data.dto.ListItem
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {

    @GET("users")
    suspend fun getUserList(): Response<List<ListItem>>
}
