package com.kyungrae.android.modelcontext

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 계산기 서비스 관리자 구현 - 독립된 서비스로 제공
 */
class ModelContextServiceImpl : Service() {

    companion object {
        private const val TAG = "ServiceManager"

        // 상수 정의
        private const val ACTION_DISCOVERY_REQUEST =
            "com.kyungrae.android.modelcontext.DISCOVERY_REQUEST"
        private const val ACTION_DISCOVERY_RESPONSE =
            "com.kyungrae.android.modelcontext.DISCOVERY_RESPONSE"

        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_CLASS_NAME = "class_name"
        private const val EXTRA_SERVICE_TYPE = "service_type"
        private const val EXTRA_SERVICE_VERSION = "service_version"

        private const val PREF_NAME = "modelcontext_app_services"
        private const val PREF_KEY_SERVICES = "discovered_services"
    }

    // 발견된 서비스 목록
    private val discoveredServices = mutableListOf<ServiceInfo>()

    // 활성 서비스 연결을 저장하는 맵
    private val serviceConnections = mutableMapOf<String, ConnectedService>()

    // AIDL 바인더 구현
    private val binder = object : IModelContextService.Stub() {
        override fun discoverServices(callback: IServiceDiscoveryCallback) {
            // 비동기적으로 서비스 검색 시작
            Thread {
                performServiceDiscovery()

                // 검색 완료 후 콜백 호출
                try {
                    callback.onServicesDiscovered(ArrayList(discoveredServices))
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error calling discovery callback", e)
                }
            }.start()
        }

        override fun getServicesByType(type: String): MutableList<ServiceInfo> {
            val result = mutableListOf<ServiceInfo>()
            for (service in discoveredServices) {
                if (service.serviceType == type) {
                    result.add(service)
                }
            }
            return result
        }

        override fun connectToService(serviceInfo: ServiceInfo): Boolean {
            return doConnectToService(serviceInfo)
        }

        override fun disconnectFromService(serviceInfo: ServiceInfo) {
            doDisconnectFromService(serviceInfo)
        }

        override fun calculate(serviceType: String, value: Int): String {
            return doCalculate(serviceType, value)
        }

        override fun isServiceTypeConnected(serviceType: String): Boolean {
            return isTypeConnected(serviceType)
        }

        override fun getServiceVersion(serviceType: String): String {
            return getVersion(serviceType)
        }
    }

    // 서비스 발견을 위한 브로드캐스트 리시버
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_DISCOVERY_RESPONSE) {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                val className = intent.getStringExtra(EXTRA_CLASS_NAME) ?: return
                val serviceType = intent.getStringExtra(EXTRA_SERVICE_TYPE) ?: "unknown"

                Log.d(
                    TAG,
                    "Received discovery response: $packageName/$className, type: $serviceType"
                )

                // 서비스 정보 객체 생성
                val service = ServiceInfo(packageName, className, serviceType)

                // 중복 제거
                if (service !in discoveredServices) {
                    discoveredServices.add(service)
                    Log.d(TAG, "Added new service to discovered list")
                }
            }
        }
    }

    // 패키지 설치/제거 모니터링 리시버
    private val packageMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val action = intent.action ?: return

            when (action) {
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    Log.d(TAG, "Package added or replaced: $packageName")

                    // 새 패키지가 계산기 서비스 앱인지 확인하기 위해 서비스 검색 요청
                    val discoveryIntent = Intent(ACTION_DISCOVERY_REQUEST)
                    discoveryIntent.setPackage(packageName)
                    sendBroadcast(discoveryIntent)

                    // 전체 서비스 재검색 수행
                    performServiceDiscovery()
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    Log.d(TAG, "Package removed: $packageName")

                    // 해당 패키지의 서비스 제거
                    val toRemove = discoveredServices.filter { it.packageName == packageName }
                    toRemove.forEach { doDisconnectFromService(it) }

                    discoveredServices.removeAll(toRemove)
                    saveServicesToCache()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CalculatorServiceManager service created")

        // 캐시에서 이전에 발견된 서비스 목록 로드
        loadCachedServices()

        // 패키지 설치/제거 모니터링을 위한 리시버 등록
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageMonitorReceiver, packageFilter)
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()

        // 모든 서비스 연결 해제
        disconnectAllServices()

        // 리시버 등록 해제
        try {
            unregisterReceiver(packageMonitorReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering package monitor receiver", e)
        }

        Log.d(TAG, "CalculatorServiceManager service destroyed")
    }

    /**
     * 전체 서비스 발견 과정 수행
     */
    private fun performServiceDiscovery() {
        Log.d(TAG, "Starting service discovery...")

        // 브로드캐스트 기반 검색
        startBroadcastDiscovery {
            // 인텐트 기반 백업 검색
            val intentServices = discoverViaIntent()

            // 중복 제거하며 결합
            for (service in intentServices) {
                if (service !in discoveredServices) {
                    discoveredServices.add(service)
                }
            }

            // 발견된 서비스 목록 캐싱
            saveServicesToCache()

            Log.d(TAG, "Service discovery complete. Found ${discoveredServices.size} services")
        }
    }

    /**
     * 브로드캐스트 기반 검색 시작
     */
    private fun startBroadcastDiscovery(onComplete: () -> Unit) {
        // 브로드캐스트 리시버 등록 (Android 13 이상에서는 RECEIVER_EXPORTED 필요)
        val filter = IntentFilter(ACTION_DISCOVERY_RESPONSE)

        // Android 버전에 따라 적절한 등록 방식 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(discoveryReceiver, filter)
        }

        // 디스커버리 요청 브로드캐스트 전송
        val discoveryIntent = Intent(ACTION_DISCOVERY_REQUEST).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendBroadcast(discoveryIntent)

        Log.d(TAG, "Sent discovery request broadcast")

        // 일정 시간 후 완료 처리
        Handler(Looper.getMainLooper()).postDelayed({
            // 브로드캐스트 리시버 등록 해제
            try {
                unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }

            // 다음 단계 진행
            onComplete()
        }, 2000) // 2초 대기
    }

    /**
     * 인텐트를 사용하여 서비스 검색 (백업 방법)
     */
    private fun discoverViaIntent(): List<ServiceInfo> {
        val services = mutableListOf<ServiceInfo>()

        // 서비스 검색을 위한 인텐트 생성
        val intent = Intent("com.kyungrae.android.modelcontext.MODELCONTEXT_APP").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        Log.d(TAG, "Searching for services with intent: ${intent.action}")

        try {
            // 매니페스트에 등록된 서비스들 찾기
            val resolveInfoList = packageManager.queryIntentServices(
                intent, PackageManager.GET_META_DATA
            )

            Log.d(TAG, "Found ${resolveInfoList.size} matching services via intent")

            for (resolveInfo in resolveInfoList) {
                val serviceInfo = resolveInfo.serviceInfo
                Log.d(TAG, "Processing service: ${serviceInfo.packageName}/${serviceInfo.name}")

                try {
                    // 서비스 유형 확인
                    var serviceType = "unknown"

                    if (serviceInfo.metaData != null && serviceInfo.metaData.containsKey("service.type")) {
                        serviceType = serviceInfo.metaData.getString("service.type") ?: "unknown"
                    } else {
                        // 패키지 이름에 기반해 타입 추측
                        serviceType = when {
                            serviceInfo.packageName.contains("date", ignoreCase = true) -> "date"
                            serviceInfo.packageName.contains("time", ignoreCase = true) -> "time"
                            else -> "unknown"
                        }
                    }

                    // 서비스 정보 객체 생성
                    val service = ServiceInfo(
                        packageName = serviceInfo.packageName,
                        className = serviceInfo.name,
                        serviceType = serviceType
                    )

                    services.add(service)

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing service", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during intent-based discovery", e)
        }

        return services
    }

    /**
     * 캐시에서 서비스 목록 로드
     */
    private fun loadCachedServices() {
        try {
            val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            val servicesJson = prefs.getString(PREF_KEY_SERVICES, null) ?: return

            val jsonArray = JSONArray(servicesJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val packageName = jsonObject.getString("packageName")
                val className = jsonObject.getString("className")
                val serviceType = jsonObject.getString("serviceType")

                // 패키지가 설치되어 있는지 확인
                if (isPackageInstalled(packageName)) {
                    val service = ServiceInfo(packageName, className, serviceType)
                    discoveredServices.add(service)
                }
            }

            Log.d(TAG, "Loaded ${discoveredServices.size} services from cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached services", e)
        }
    }

    /**
     * 패키지가 설치되어 있는지 확인
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 서비스 목록을 캐시에 저장
     */
    private fun saveServicesToCache() {
        try {
            val jsonArray = JSONArray()
            for (service in discoveredServices) {
                val jsonObject = JSONObject().apply {
                    put("packageName", service.packageName)
                    put("className", service.className)
                    put("serviceType", service.serviceType)
                }
                jsonArray.put(jsonObject)
            }

            getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SERVICES, jsonArray.toString())
                .apply()

            Log.d(TAG, "Saved ${discoveredServices.size} services to cache")
        } catch (e: JSONException) {
            Log.e(TAG, "Error saving services to cache", e)
        }
    }

    /**
     * 서비스에 연결
     */
    private fun doConnectToService(serviceInfo: ServiceInfo): Boolean {
        // 이미 연결된 서비스인지 확인
        val serviceKey = "${serviceInfo.packageName}/${serviceInfo.className}"
        if (serviceConnections.containsKey(serviceKey)) {
            Log.d(TAG, "Already connected to service: $serviceKey")
            return true
        }

        // 서비스 연결 객체 생성
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.d(TAG, "Service connected: $name")

                try {
                    // AIDL 인터페이스 획득
                    val contextApp = IModelContextApp.Stub.asInterface(service)

                    // 실제로 서비스 호출 가능한지 확인
                    val serviceType = try {
                        contextApp.serviceType
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling getServiceType()", e)
                        "unknown"
                    }

                    Log.d(TAG, "Service type from call: $serviceType")

                    // 연결된 서비스 정보 저장
                    serviceConnections[serviceKey] = ConnectedService(
                        serviceInfo = serviceInfo,
                        serviceConnection = this,
                        modelContextApp = contextApp
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up service interface", e)
                    try {
                        unbindService(this)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error unbinding from service after connection error", e2)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(TAG, "Service disconnected: $name")

                // 연결 정보 삭제
                serviceConnections.remove(serviceKey)
            }
        }

        // 서비스 바인딩 시도
        Log.d(TAG, "Attempting to bind to service: $serviceKey")

        return try {
            // 명시적 인텐트 생성
            val intent = Intent().apply {
                component = ComponentName(serviceInfo.packageName, serviceInfo.className)
                action = "com.kyungrae.android.modelcontext.MODELCONTEXT_APP" // 안전을 위해 액션도 설정
            }

            // 서비스 바인딩
            val result = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Bind result: $result")

            if (!result) {
                Log.e(TAG, "Failed to bind to service: $serviceKey")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to service: $serviceKey", e)
            false
        }
    }

    /**
     * 서비스 연결 해제
     */
    private fun doDisconnectFromService(serviceInfo: ServiceInfo) {
        val serviceKey = "${serviceInfo.packageName}/${serviceInfo.className}"

        serviceConnections[serviceKey]?.let { connectedService ->
            try {
                unbindService(connectedService.serviceConnection)
                serviceConnections.remove(serviceKey)
                Log.d(TAG, "Disconnected from service: $serviceKey")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding from service: $serviceKey", e)
            }
        }
    }

    /**
     * 모든 서비스 연결 해제
     */
    private fun disconnectAllServices() {
        for ((serviceKey, connectedService) in serviceConnections) {
            try {
                unbindService(connectedService.serviceConnection)
                Log.d(TAG, "Disconnected from service: $serviceKey")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding from service: $serviceKey", e)
            }
        }
        serviceConnections.clear()
    }

    /**
     * 계산 실행
     */
    private fun doCalculate(serviceType: String, value: Int): String {
        // 해당 타입의 연결된 서비스 찾기
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.calculate(value)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error calling calculate() on service", e)
                    "서비스 호출 중 오류가 발생했습니다"
                }
            }
        }

        return "서비스가 연결되지 않았습니다"
    }

    /**
     * 특정 타입의 서비스가 연결되었는지 확인
     */
    private fun isTypeConnected(serviceType: String): Boolean {
        return serviceConnections.values.any { it.serviceInfo.serviceType == serviceType }
    }

    /**
     * 서비스 버전 정보 조회
     */
    private fun getVersion(serviceType: String): String {
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.serviceVersion
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error calling getServiceVersion() on service", e)
                    "알 수 없음"
                }
            }
        }

        return "알 수 없음"
    }

    /**
     * 연결된 서비스 정보 클래스
     */
    private data class ConnectedService(
        val serviceInfo: ServiceInfo,
        val serviceConnection: ServiceConnection,
        val modelContextApp: IModelContextApp
    )
}