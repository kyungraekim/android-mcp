package com.kyungrae.android.mcp.api

import com.kyungrae.android.mcp.model.ChatCompletionRequest
import com.kyungrae.android.mcp.model.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AzureOpenAIApi {
    @POST("openai/deployments/{deployment-id}/chat/completions")
    suspend fun createChatCompletion(
        @Path("deployment-id") deploymentId: String,
        @Query("api-version") apiVersion: String,
        @Header("api-key") apiKey: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}