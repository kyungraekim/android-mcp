package com.kyungrae.android.timecalculator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kyungrae.android.modelcontext.ContentItem
import com.kyungrae.android.modelcontext.IModelContextApp
import com.kyungrae.android.modelcontext.ResourceInfo
import com.kyungrae.android.modelcontext.ToolInfo
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Enhanced Time Calculator Service implementing MCP-like functionality
 */
class TimeCalculatorService : Service() {

    private val TAG = "TimeCalculatorService"

    // Tools provided by this service
    private val availableTools = listOf(
        ToolInfo(
            name = "add_hours",
            description = "Adds specified number of hours to the current time",
            inputSchema = buildInputSchema(
                "object",
                mapOf(
                    "hours" to mapOf(
                        "type" to "number",
                        "description" to "Number of hours to add"
                    )
                ),
                listOf("hours")
            ),
            isError = false
        ),
        ToolInfo(
            name = "add_minutes",
            description = "Adds specified number of minutes to the current time",
            inputSchema = buildInputSchema(
                "object",
                mapOf(
                    "minutes" to mapOf(
                        "type" to "number",
                        "description" to "Number of minutes to add"
                    )
                ),
                listOf("minutes")
            ),
            isError = false
        )
    )

    // Resources provided by this service
    private val availableResources = listOf(
        ResourceInfo(
            uri = "time://current",
            name = "Current Time",
            description = "Current time information",
            mimeType = "text/plain"
        ),
        ResourceInfo(
            uri = "time://zones",
            name = "Time Zones",
            description = "List of available time zones",
            mimeType = "application/json"
        )
    )

    // AIDL interface implementation
    private val binder = object : IModelContextApp.Stub() {
        override fun getServiceType(): String {
            return "time"
        }

        override fun getServiceVersion(): String {
            return "TimeCalculator Service v1.1"
        }

        // Basic calculation (for backward compatibility)
        override fun calculate(value: String): String {
            return try {
                addHours(value.toInt())
            } catch (_: NumberFormatException) {
                "Error: Invalid number format"
            }
        }

        // MCP-like tool listing
        override fun listTools(): MutableList<ToolInfo> {
            return availableTools.toMutableList()
        }

        // MCP-like tool calling
        override fun callTool(name: String, jsonArguments: String): MutableList<ContentItem> {
            Log.d(TAG, "callTool: $name with arguments: $jsonArguments")

            return try {
                when (name) {
                    "add_hours" -> {
                        val args = JSONObject(jsonArguments)
                        val hours = args.getInt("hours")
                        val result = addHours(hours)
                        mutableListOf(ContentItem.createTextContent(result))
                    }

                    "add_minutes" -> {
                        val args = JSONObject(jsonArguments)
                        val minutes = args.getInt("minutes")
                        val result = addMinutes(minutes)
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
        override fun listResources(): MutableList<ResourceInfo> {
            return availableResources.toMutableList()
        }

        // MCP-like resource reading
        override fun readResource(uri: String): MutableList<ContentItem> {
            Log.d(TAG, "readResource: $uri")

            return when (uri) {
                "time://current" -> {
                    val current = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Calendar.getInstance().time)
                    mutableListOf(ContentItem.createTextContent(current))
                }

                "time://zones" -> {
                    val zones = TimeZone.getAvailableIDs()
                    val jsonArray = org.json.JSONArray()
                    zones.take(50).forEach { jsonArray.put(it) } // Limit to 50 zones

                    val jsonObject = JSONObject().apply {
                        put("zones", jsonArray)
                        put("defaultZone", TimeZone.getDefault().id)
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
            return when (capability) {
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

    // Helper methods for calculations
    private fun addHours(hours: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, hours)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val resultTime = dateFormat.format(calendar.time)

        Log.d(TAG, "Adding $hours hours, result: $resultTime")
        return resultTime
    }

    private fun addMinutes(minutes: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, minutes)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val resultTime = dateFormat.format(calendar.time)

        Log.d(TAG, "Adding $minutes minutes, result: $resultTime")
        return resultTime
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
                val propObj = JSONObject()
                details.forEach { (key, value) ->
                    propObj.put(key, value)
                }
                propertiesObj.put(name, propObj)
            }
            put("properties", propertiesObj)

            val requiredArray = org.json.JSONArray()
            required.forEach { requiredArray.put(it) }
            put("required", requiredArray)
        }

        return jsonObject.toString()
    }
}