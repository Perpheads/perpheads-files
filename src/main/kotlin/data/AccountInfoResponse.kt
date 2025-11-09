package com.perpheads.files.data

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

class AccountInfoResponse(
    @JsonSerialize(using = ToStringSerializer::class)
    val communityId: Long,
    val userId: Int,
    val name: String,
    val admin: Boolean,
)

fun User.toAccountInfoResponse(): AccountInfoResponse {
    return AccountInfoResponse(
        communityId = communityId,
        userId = userId,
        name = name,
        admin = admin
    )
}