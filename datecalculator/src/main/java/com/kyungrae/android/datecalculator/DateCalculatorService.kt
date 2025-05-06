package com.kyungrae.android.datecalculator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kyungrae.android.modelcontext.IModelContextApp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 날짜 계산 서비스
 */
class DateCalculatorService : Service() {

    private val TAG = "DateCalculatorService"

    // AIDL 인터페이스 구현
    private val binder = object : IModelContextApp.Stub() {

        override fun getServiceType(): String {
            return "date"
        }

        override fun calculate(value: Int): String {
            Log.d(TAG, "calculate called with days: $value")

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, value)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val resultDate = dateFormat.format(calendar.time)

            Log.d(TAG, "Result date: $resultDate")
            return resultDate
        }

        override fun getServiceVersion(): String {
            return "DateCalculator Service v1.0"
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