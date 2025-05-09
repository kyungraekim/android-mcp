package com.kyungrae.android.mcp.model

data class FunctionParameters(
    val type: String,
    val properties: Map<String, PropertyDefinition>,
    val required: List<String>? = null
)
