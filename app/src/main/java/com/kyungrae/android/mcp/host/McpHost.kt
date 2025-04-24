package com.kyungrae.android.mcp.host

import android.content.Context

/**
 * Interface for the language model that will make decisions about which tools to use
 */
interface LanguageModelInterface {
}


class McpHost(private val context: Context) {
    /**
     * Initialize the MCP host with all available connections
     */
    suspend fun initialize(languageModel: LanguageModelInterface) {

    }

    suspend fun processUserInput(userInput: String): String {
        return userInput
    }

    /**
     * Clean up all connections
     */
    suspend fun shutdown() {

    }
}