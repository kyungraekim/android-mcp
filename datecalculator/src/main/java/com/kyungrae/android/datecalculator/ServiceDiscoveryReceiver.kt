package com.kyungrae.android.datecalculator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 서비스 검색 요청에 응답하는 브로드캐스트 리시버
 */
class ServiceDiscoveryReceiver : BroadcastReceiver() {

    private val TAG = "ServiceDiscoveryReceiver"

    companion object {
        const val ACTION_DISCOVERY_REQUEST = "com.kyungrae.android.modelcontext.DISCOVERY_REQUEST"
        const val ACTION_DISCOVERY_RESPONSE = "com.kyungrae.android.modelcontext.DISCOVERY_RESPONSE"

        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_CLASS_NAME = "class_name"
        const val EXTRA_SERVICE_TYPE = "service_type"
        const val EXTRA_SERVICE_VERSION = "service_version"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISCOVERY_REQUEST) {
            Log.d(TAG, "Received discovery request, sending response")

            // 응답 브로드캐스트 준비
            val responseIntent = Intent(ACTION_DISCOVERY_RESPONSE)

            // 이 앱의 서비스 정보 추가
            responseIntent.putExtra(EXTRA_PACKAGE_NAME, context.packageName)
            responseIntent.putExtra(EXTRA_CLASS_NAME, "com.kyungrae.android.datecalculator.DateCalculatorService")
            responseIntent.putExtra(EXTRA_SERVICE_TYPE, "date") // 서비스 유형
            responseIntent.putExtra(EXTRA_SERVICE_VERSION, "1.0") // 서비스 버전

            // 응답 전송
            context.sendBroadcast(responseIntent)
            Log.d(TAG, "Sent discovery response")
        }
    }
}