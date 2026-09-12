package com.example.sampleapp.fake

import com.example.sampleapp.domain.model.UserUI
import com.example.sampleapp.domain.repository.ListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeListRepository : ListRepository {
    var result: Result<List<UserUI>> = Result.success(defaultUsers())
    override fun getUsers(): Flow<Result<List<UserUI>>> = flowOf(result)
}

fun defaultUsers() = listOf(
    UserUI(1, "Leanne Graham", "Bret",      "sincere@april.biz", "1-770-736-8031", "hildegard.org"),
    UserUI(2, "Ervin Howell",  "Antonette", "shanna@melissa.tv", "010-692-6593",   "anastasia.net"),
)
