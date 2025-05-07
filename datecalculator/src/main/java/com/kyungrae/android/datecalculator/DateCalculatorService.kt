package com.kyungrae.android.datecalculator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kyungrae.android.modelcontext.ContentItem
import com.kyungrae.android.modelcontext.IModelContextApp
import com.kyungrae.android.modelcontext.ResourceInfo
import com.kyungrae.android.modelcontext.ToolInfo
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 날짜 계산 서비스
 */
class DateCalculatorService : Service() {

    private val TAG = "DateCalculatorService"

    // Tools provided by this service
    private val availableTools = listOf(
        ToolInfo(
            name = "add_days",
            description = "Adds specified number of days to the current date",
            inputSchema = buildInputSchema(
                "object",
                mapOf(
                    "days" to mapOf(
                        "type" to "number",
                        "description" to "Number of days to add"
                    )
                ),
                listOf("days")
            ),
            isError = false
        ),
        ToolInfo(
            name = "add_months",
            description = "Adds specified number of months to the current date",
            inputSchema = buildInputSchema(
                "object",
                mapOf(
                    "months" to mapOf(
                        "type" to "number",
                        "description" to "Number of months to add"
                    )
                ),
                listOf("months")
            ),
            isError = false
        )
    )

    // Resources provided by this service
    private val availableResources = listOf(
        ResourceInfo(
            uri = "date://current",
            name = "Current Date",
            description = "Current date information",
            mimeType = "text/plain"
        ),
        ResourceInfo(
            uri = "date://calendar",
            name = "Calendar Information",
            description = "Current calendar information including year, month, day",
            mimeType = "application/json"
        )
    )

    // AIDL 인터페이스 구현
    private val binder = object : IModelContextApp.Stub() {
        override fun getServiceType(): String = "date"
        override fun getServiceVersion(): String = "DateCalculator Service v1.1"

        // Basic calculation (for backward compatibility)
        override fun calculate(value: String): String {
            return try {
                addDays(value.toInt())
            } catch (e: NumberFormatException) {
                "Error: Invalid number format"
            }
        }

        // MCP-like tool listing
        override fun listTools(): MutableList<ToolInfo> = availableTools.toMutableList()

        // MCP-like tool calling
        override fun callTool(name: String, jsonArguments: String): MutableList<ContentItem> {
            Log.d(TAG, "callTool: $name with arguments: $jsonArguments")

            return try {
                when(name) {
                    "add_days" -> {
                        val args = JSONObject(jsonArguments)
                        val days = args.getInt("days")
                        val result = addDays(days)
                        mutableListOf(ContentItem.createTextContent(result))
                    }
                    "add_months" -> {
                        val args = JSONObject(jsonArguments)
                        val months = args.getInt("months")
                        val result = addMonths(months)
                        mutableListOf(ContentItem.createTextContent(result))
                    }
                    else -> {
                        Log.w(TAG, "Unknown tool: $name")
                        mutableListOf(ContentItem.createErrorContent("Unknown tool: $name"))
                    }
                }
            } catch (e: JSONException) {
                Log.e(TAG, "JSON parsing error: ${e.message}")
                mutableListOf(ContentItem.createErrorContent("Invalid arguments: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "Error calling tool: ${e.message}")
                mutableListOf(ContentItem.createErrorContent("Error: ${e.message}"))
            }
        }

        // MCP-like resource listing
        override fun listResources(): List<ResourceInfo> = availableResources.toMutableList()

        // MCP-like resource reading
        override fun readResource(uri: String): MutableList<ContentItem> {
            Log.d(TAG, "readResource: $uri")

            return when(uri) {
                "date://current" -> {
                    val current = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                        Calendar.getInstance().time)
                    mutableListOf(ContentItem.createTextContent(current))
                }
                "data://calendar" -> {
                    val calendar = Calendar.getInstance()
                    val jsonObject = JSONObject().apply {
                        put("year", calendar.get(Calendar.YEAR))
                        put("month", calendar.get(Calendar.MONTH) + 1)
                        put("day", calendar.get(Calendar.DAY_OF_MONTH))
                        put("dayOfWeek", calendar.get(Calendar.DAY_OF_WEEK))
                        put("dayOfYear", calendar.get(Calendar.DAY_OF_YEAR))
                    }
                    mutableListOf(ContentItem("json", jsonObject.toString(), "application/json"))
                }
                else -> {
                    Log.w(TAG, "Unknown resource URI: $uri")
                    mutableListOf(ContentItem.createErrorContent("Unknown resource: $uri"))
                }
            }
        }

        // Capability checking
        override fun hasCapability(capability: String): Boolean {
            return when(capability) {
                "tools" -> true
                "resources" -> true
                else -> false
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    // Helper method for calculations
    private fun addDays(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val resultDate = dateFormat.format(calendar.time)

        Log.d(TAG, "Adding $days days, result: $resultDate")
        return resultDate
    }

    private fun addMonths(months: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, months)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val resultDate = dateFormat.format(calendar.time)

        Log.d(TAG, "Adding $months months, result: $resultDate")
        return resultDate
    }

    // Helper method to build JSON schema
    private fun buildInputSchema(
        type: String,
        properties: Map<String, Map<String, String>>,
        required: List<String>
    ): String {
        val jsonObject = JSONObject().apply {
            put("type", type)

            val propertiesObj = JSONObject()
            properties.forEach { (name, details) ->
                val propertyObj = JSONObject()
                details.forEach { (key, value) ->
                    propertyObj.put(key, value)
                }
                propertiesObj.put(name, propertyObj)
            }
            put("properties", propertiesObj)

            val requiredArray = JSONArray()
            required.forEach { requiredArray.put(it) }
            put("required", requiredArray)
        }
        return jsonObject.toString()
    }
}