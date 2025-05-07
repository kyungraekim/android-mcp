package com.kyungrae.android.debug_app.adapter


data class CalendarEventData(
    val title: String,
    val location: String,
    val day: String, // 형식: "YYYY-MM-DD"
    val startTime: String, // 형식: "HH:mm"
    val durationMinutes: Int // 분 단위
)