package com.kyungrae.android.mcp.api

import com.kyungrae.android.mcp.BuildConfig
import com.kyungrae.android.mcp.model.FunctionDefinition
import com.kyungrae.android.mcp.model.FunctionParameters
import com.kyungrae.android.mcp.model.PropertyDefinition
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Azure OpenAI 설정
    private const val AZURE_OPENAI_ENDPOINT = BuildConfig.AZURE_OPENAI_ENDPOINT
    private const val AZURE_OPENAI_API_KEY = BuildConfig.AZURE_OPENAI_API_KEY
    private const val AZURE_OPENAI_API_VERSION = BuildConfig.AZURE_OPENAI_API_VERSION
    private const val AZURE_OPENAI_DEPLOYMENT_NAME = BuildConfig.AZURE_OPENAI_DEPLOYMENT_NAME

    // 함수 정의
    val functions = listOf(
        FunctionDefinition(
            name = "calculateDate",
            description = "현재 날짜로부터 지정된 일수를 더하거나 뺀 날짜와 시간을 반환합니다.",
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "days" to PropertyDefinition(
                        type = "integer",
                        description = "더하거나 뺄 일수 (양수: 미래, 음수: 과거)"
                    )
                ),
                required = listOf("days")
            )
        ),
        FunctionDefinition(
            name = "addSchedule",
            description = "일정을 등록합니다.",
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "datetime" to PropertyDefinition(
                        type = "string",
                        description = "ISO 형식의 날짜 문자열 (YYYY-MM-DD HH:MM)"
                    ),
                    "title" to PropertyDefinition(
                        type = "string",
                        description = "일정 제목"
                    ),
                    "location" to PropertyDefinition(
                        type = "string",
                        description = "장소"
                    )
                ),
                required = listOf("datetime")
            )
        )
    )

    private val httpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val azureOpenAiApi: AzureOpenAIApi by lazy {
        Retrofit.Builder()
            .baseUrl(AZURE_OPENAI_ENDPOINT)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AzureOpenAIApi::class.java)
    }

    // Azure OpenAI API 인증 헤더
    fun getApiKeyHeader(): String = AZURE_OPENAI_API_KEY

    // Azure OpenAI API 버전
    fun getApiVersion(): String = AZURE_OPENAI_API_VERSION

    // Azure OpenAI 배포 이름
    fun getDeploymentName(): String = AZURE_OPENAI_DEPLOYMENT_NAME
}