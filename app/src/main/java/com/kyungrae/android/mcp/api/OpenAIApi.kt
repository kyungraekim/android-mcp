package com.kyungrae.android.mcp.api

import com.kyungrae.android.mcp.model.ChatCompletionRequest
import com.kyungrae.android.mcp.model.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAIApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}