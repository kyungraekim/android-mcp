package com.kyungrae.android.modelcontext.adapter

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.google.gson.Gson
import com.kyungrae.android.modelcontext.IModelContextApp
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarScheduler(
    private val context: Context
): IModelContextApp.Stub() {
    private val TAG = "CalendarScheduler"

    override fun getServiceType(): String {
        return "schedule"
    }

    override fun calculate(value: String?): String {
        return addCalendarEvent(Gson().fromJson(value, CalendarEventData::class.java))
    }

    override fun getServiceVersion(): String {
        return "CalendarScheduler Adapter v1.0"
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
                // 필요시 Handler(Looper.getMainLooper()).post { Toast.makeText(...) }
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
            return e.message?: "Parsing error"
        } catch (e: Exception) {
            Log.e(TAG, "캘린더 인텐트 실행 오류", e)
            return e.message?: "Intent error"
        }
        return "Success"
    }
}