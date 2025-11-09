package com.perpheads.files.data

class User(
    val userId: Int,
    val name: String,
    val communityId: Long,
    val apiKey: String?,
    val admin: Boolean
)