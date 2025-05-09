package com.kyungrae.android.mcp.model

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val functions: List<FunctionDefinition>? = null,
    val function_call: String? = null
)