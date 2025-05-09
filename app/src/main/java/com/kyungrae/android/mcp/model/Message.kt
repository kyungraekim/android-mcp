package com.kyungrae.android.mcp.model

data class Message(
    val role: String,
    val content: String,
    val name: String? = null,
    val function_call: FunctionCall? = null
)
