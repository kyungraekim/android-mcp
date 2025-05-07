package com.kyungrae.android.modelcontext.adapter

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.google.gson.Gson
import com.kyungrae.android.modelcontext.*
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Calendar Scheduler implementing MCP-like functionality
 */
class CalendarScheduler(
    private val context: Context
) : IModelContextApp.Stub() {
    private val TAG = "CalendarScheduler"

    // Tools provided by this service
    private val availableTools = listOf(
        ToolInfo(
            name = "add_event",
            description = "Adds an event to the calendar",
            inputSchema = buildInputSchema(
                "object",
                mapOf(
                    "title" to mapOf("type" to "string", "description" to "Event title"),
                    "location" to mapOf("type" to "string", "description" to "Event location"),
                    "day" to mapOf("type" to "string", "description" to "Event date (YYYY-MM-DD)"),
                    "startTime" to mapOf("type" to "string", "description" to "Start time (HH:mm)"),
                    "durationMinutes" to mapOf("type" to "number", "description" to "Duration in minutes")
                ),
                listOf("title", "day", "startTime", "durationMinutes")
            ),
            isError = false
        ),
        ToolInfo(
            name = "query_events",
            description = "Queries upcoming events from the calendar",
            inputSchema = buildInputSchema(
                "object",
                mapOf("days" to mapOf("type" to "number", "description" to "Number of days to look ahead")),
                listOf("days")
            ),
            isError = false
        )
    )

    // Resources provided by this service
    private val availableResources = listOf(
        ResourceInfo(
            uri = "calendar://upcoming",
            name = "Upcoming Events",
            description = "List of upcoming calendar events",
            mimeType = "application/json"
        )
    )

    override fun getServiceType(): String {
        return "schedule"
    }

    override fun getServiceVersion(): String {
        return "CalendarScheduler Adapter v1.1"
    }

    override fun calculate(value: String): String {
        // For backward compatibility, parse the value as a CalendarEventData
        return try {
            val eventData = Gson().fromJson(value, CalendarEventData::class.java)
            addCalendarEvent(eventData)
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculate method", e)
            "Error: ${e.message}"
        }
    }

    override fun listTools(): MutableList<ToolInfo> {
        return availableTools.toMutableList()
    }

    override fun callTool(name: String, jsonArguments: String): MutableList<ContentItem> {
        Log.d(TAG, "callTool: $name with arguments: $jsonArguments")

        return try {
            when(name) {
                "add_event" -> {
                    val args = JSONObject(jsonArguments)
                    val eventData = CalendarEventData(
                        title = args.getString("title"),
                        location = args.optString("location", ""),
                        day = args.getString("day"),
                        startTime = args.getString("startTime"),
                        durationMinutes = args.getInt("durationMinutes")
                    )
                    val result = addCalendarEvent(eventData)
                    mutableListOf(ContentItem.createTextContent(result))
                }
                "query_events" -> {
                    val args = JSONObject(jsonArguments)
                    val days = args.getInt("days")
                    val events = queryUpcomingEvents(days)
                    mutableListOf(ContentItem("json", events, "application/json"))
                }
                else -> {
                    Log.w(TAG, "Unknown tool: $name")
                    mutableListOf(ContentItem.createErrorContent("Unknown tool: $name"))
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: ${e.message}", e)
            mutableListOf(ContentItem.createErrorContent("Invalid arguments: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool: ${e.message}", e)
            mutableListOf(ContentItem.createErrorContent("Error: ${e.message}"))
        }
    }

    override fun listResources(): MutableList<ResourceInfo> {
        return availableResources.toMutableList()
    }

    override fun readResource(uri: String): MutableList<ContentItem> {
        Log.d(TAG, "readResource: $uri")

        return when(uri) {
            "calendar://upcoming" -> {
                val events = queryUpcomingEvents(7) // Default to 7 days
                mutableListOf(ContentItem("json", events, "application/json"))
            }
            else -> {
                Log.w(TAG, "Unknown resource URI: $uri")
                mutableListOf(ContentItem.createErrorContent("Unknown resource: $uri"))
            }
        }
    }

    override fun hasCapability(capability: String): Boolean {
        return when(capability) {
            "tools" -> true
            "resources" -> true
            else -> false
        }
    }

    private fun addCalendarEvent(eventData: CalendarEventData?): String {
        if (eventData == null) {
            Log.d(TAG, "이벤트 데이터가 null입니다.")
            return "eventData is null"
        }

        Log.d(TAG, "서비스에서 일정 추가 시도: ${eventData.title} ${eventData.day} ${eventData.startTime}")

        try {
            // 1. 날짜 및 시간 파싱
            val dateTimeString = "${eventData.day} ${eventData.startTime}" // "YYYY-MM-DD HH:mm"
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val startDate: Date? = sdf.parse(dateTimeString)

            if (startDate == null) {
                Log.d(TAG, "날짜/시간 형식 파싱 오류: $dateTimeString")
                return "startDate is null"
            }

            val startMillis = startDate.time
            val endMillis = startMillis + eventData.durationMinutes * 60 * 1000L // 분 단위를 밀리초로

            // 2. 캘린더 인텐트 생성 (ACTION_INSERT)
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                putExtra(CalendarContract.Events.TITLE, eventData.title)
                putExtra(CalendarContract.Events.EVENT_LOCATION, eventData.location)
                // 서비스에서 액티비티 시작 시 FLAG_ACTIVITY_NEW_TASK 필요
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 3. 인텐트 실행
            if (intent.resolveActivity(context.packageManager) != null) {
                Log.d(TAG, "인텐트 실행")
                context.startActivity(intent)
            } else {
                Log.d(TAG, "캘린더 앱을 찾을 수 없습니다.")
                return "Cannot find calendar app"
            }

        } catch (e: ParseException) {
            Log.e(TAG, "날짜/시간 파싱 오류", e)
            return e.message ?: "Parsing error"
        } catch (e: Exception) {
            Log.e(TAG, "캘린더 인텐트 실행 오류", e)
            return e.message ?: "Intent error"
        }
        return "Success: Event scheduled"
    }

    private fun queryUpcomingEvents(days: Int): String {
        // This is a simplified mock implementation since actual Calendar query
        // would require ContentResolver and permissions
        val calendar = Calendar.getInstance()
        val today = calendar.time

        val events = JSONObject().apply {
            put("period", "$days days")

            val eventsArray = org.json.JSONArray()

            // Just create some mock events for demonstration
            for (i in 0 until 3) {
                calendar.time = today
                calendar.add(Calendar.DAY_OF_YEAR, i)

                val event = JSONObject().apply {
                    put("title", "Sample Event ${i+1}")
                    put("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time))
                    put("startTime", "${9 + i}:00")
                    put("duration", "60 minutes")
                    put("location", "Office")
                }

                eventsArray.put(event)
            }

            put("events", eventsArray)
        }

        return events.toString()
    }

    // Helper method to build JSON schema
    private fun buildInputSchema(type: String, properties: Map<String, Map<String, String>>, required: List<String>): String {
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