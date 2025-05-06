package com.kyungrae.android.timecalculator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kyungrae.android.modelcontext.IModelContextApp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 시간 계산 서비스
 */
class TimeCalculatorService : Service() {

    private val TAG = "TimeCalculatorService"

    // AIDL 인터페이스 구현
    private val binder = object : IModelContextApp.Stub() {

        override fun getServiceType(): String {
            return "time"
        }

        override fun calculate(value: Int): String {
            Log.d(TAG, "calculate called with hours: $value")

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, value)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val resultTime = dateFormat.format(calendar.time)

            Log.d(TAG, "Result time: $resultTime")
            return resultTime
        }

        override fun getServiceVersion(): String {
            return "TimeCalculator Service v1.0"
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
}