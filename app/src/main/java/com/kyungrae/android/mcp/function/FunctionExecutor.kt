package com.kyungrae.android.mcp.function

import com.google.gson.Gson
import com.kyungrae.android.mcp.model.FunctionCall
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// 함수 실행 결과
data class FunctionResult(
    val result: String
)

// 함수 실행기
object FunctionExecutor {
    private val gson = Gson()

    fun executeFunction(functionCall: FunctionCall): String {
        return when (functionCall.name) {
            "calculateDate" -> {
                val args = gson.fromJson(functionCall.arguments, CalculateDateArgs::class.java)
                val result = calculateDate(args.days)
                gson.toJson(FunctionResult(result))
            }

            "addSchedule" -> {
                val args = gson.fromJson(functionCall.arguments, AddScheduleArgs::class.java)
                val result = addSchedule(
                    args.datetime,
                    args.title ?: "일정",
                    args.location ?: ""
                )
                gson.toJson(FunctionResult(result))
            }

            else -> {
                gson.toJson(FunctionResult("Unknown function: ${functionCall.name}"))
            }
        }
    }

    // calculateDate 함수 구현
    private fun calculateDate(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    // addSchedule 함수 구현
    private fun addSchedule(datetime: String, title: String, location: String): String {
        // 실제로는 DB에 저장하는 로직이 들어갈 수 있습니다
        val scheduleId = "schedule-${UUID.randomUUID().toString().substring(0, 8)}"
        return "일정 '${title}'이 ${datetime}에 ${location}에서 등록되었습니다. (ID: $scheduleId)"
    }
}

// 함수 인자 클래스들
data class CalculateDateArgs(
    val days: Int
)

data class AddScheduleArgs(
    val datetime: String,
    val title: String? = null,
    val location: String? = null
)