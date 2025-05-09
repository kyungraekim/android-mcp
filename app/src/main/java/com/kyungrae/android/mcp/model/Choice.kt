package com.kyungrae.android.mcp.model

data class Choice(
    val index: Int,
    val message: Message,
    val finish_response: String
)