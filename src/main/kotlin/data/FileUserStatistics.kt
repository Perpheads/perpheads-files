package com.perpheads.files.data

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

class FileUserStatistics(
    @JsonSerialize(using = ToStringSerializer::class)
    val communityId: Long,
    val name: String,
    val fileCount: Int,
    val storageUsed: Long
)