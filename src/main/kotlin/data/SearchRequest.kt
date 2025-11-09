package com.perpheads.files.data

data class SearchRequest(
    val query: String,
    val beforeId: Int? = null,
    val page: Int? = null,
    val entriesPerPage: Int
)