package com.kyungrae.android.debug_app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.kyungrae.android.debug_app.adapter.CalendarScheduler
import com.kyungrae.android.modelcontext.ContentItem
import com.kyungrae.android.modelcontext.IModelContextApp
import com.kyungrae.android.modelcontext.IModelContextService
import com.kyungrae.android.modelcontext.IServiceDiscoveryCallback
import com.kyungrae.android.modelcontext.ResourceInfo
import com.kyungrae.android.modelcontext.ServiceInfo
import com.kyungrae.android.modelcontext.ToolInfo
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * MCP Service implementation
 * Acts as a central hub for discovering and connecting to MCP-compatible services
 */
class ModelContextServiceImpl : Service() {

    companion object {
        private const val TAG = "MCPService"

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

    // Discovered services list
    private val discoveredServices = mutableListOf<ServiceInfo>()

    // Build-in adapters
    private val adapters = mutableMapOf<String, Adapter>()

    // Active service connections
    private val serviceConnections = mutableMapOf<String, ConnectedService>()

    // AIDL interface implementation
    private val binder = object : IModelContextService.Stub() {
        override fun discoverServices(callback: IServiceDiscoveryCallback) {
            // Start asynchronous service discovery
            Thread {
                performServiceDiscovery()

                // Call callback when discovery completes
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

        override fun calculate(serviceType: String, value: String): String {
            return doCalculate(serviceType, value)
        }

        override fun isServiceTypeConnected(serviceType: String): Boolean {
            return isTypeConnected(serviceType)
        }

        override fun getServiceVersion(serviceType: String): String {
            return getVersion(serviceType)
        }

        // MCP-like tool operations
        override fun listTools(serviceType: String): MutableList<ToolInfo> {
            return getToolsList(serviceType)
        }

        override fun callTool(serviceType: String, toolName: String, jsonArguments: String): MutableList<ContentItem> {
            return doCallTool(serviceType, toolName, jsonArguments)
        }

        // MCP-like resource operations
        override fun listResources(serviceType: String): MutableList<ResourceInfo> {
            return getResourcesList(serviceType)
        }

        override fun readResource(serviceType: String, uri: String): MutableList<ContentItem> {
            return doReadResource(serviceType, uri)
        }

        // Capability checking
        override fun serviceHasCapability(serviceType: String, capability: String): Boolean {
            return checkServiceCapability(serviceType, capability)
        }
    }

    // Broadcast receiver for service discovery
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

                // Create service info object
                val service = ServiceInfo(packageName, className, serviceType)

                // Avoid duplicates
                if (service !in discoveredServices) {
                    discoveredServices.add(service)
                    Log.d(TAG, "Added new service to discovered list")
                }
            }
        }
    }

    // Package installation/removal monitoring receiver
    private val packageMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val action = intent.action ?: return

            when (action) {
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    Log.d(TAG, "Package added or replaced: $packageName")

                    // Check if the new package is MCP app
                    val discoveryIntent = Intent(ACTION_DISCOVERY_REQUEST)
                    discoveryIntent.setPackage(packageName)
                    sendBroadcast(discoveryIntent)

                    // Perform full service discovery
                    performServiceDiscovery()
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    Log.d(TAG, "Package removed: $packageName")

                    // Remove services from that package
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
        Log.d(TAG, "MCP service created")

        // Load previously discovered services from cache
        loadCachedServices()

        // Register receiver for package monitoring
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageMonitorReceiver, packageFilter)

        // Add built-in adapters
        addDefaultAdapters()
    }

    private fun addDefaultAdapters() {
        val adapter = CalendarScheduler(context = ContextWrapper(applicationContext))
        val serviceInfo = ServiceInfo(
            packageName = adapter::class.java.`package`?.name ?: "",
            className = adapter::class.simpleName ?: "",
            serviceType = adapter.serviceType
        )
        discoveredServices.add(serviceInfo)
        val serviceKey = "${serviceInfo.packageName}/${serviceInfo.className}"
        adapters[serviceKey] = Adapter(serviceInfo, adapter)
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()

        // Disconnect all services
        disconnectAllServices()

        // Unregister receivers
        try {
            unregisterReceiver(packageMonitorReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering package monitor receiver", e)
        }

        Log.d(TAG, "MCP service destroyed")
    }

    /**
     * Perform full service discovery process
     */
    private fun performServiceDiscovery() {
        Log.d(TAG, "Starting service discovery...")

        // Broadcast-based discovery
        startBroadcastDiscovery {
            // Intent-based backup discovery
            val intentServices = discoverViaIntent()

            // Combine without duplicates
            for (service in intentServices) {
                if (service !in discoveredServices) {
                    discoveredServices.add(service)
                }
            }

            // Cache discovered services list
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
        registerReceiver(discoveryReceiver, filter, RECEIVER_EXPORTED)

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
        } catch (_: PackageManager.NameNotFoundException) {
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
            val result = bindService(intent, serviceConnection, BIND_AUTO_CREATE)
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
    private fun doCalculate(serviceType: String, value: String): String {
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return adapter.modelContextApp.calculate(value)
            }
        }
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
     * Get list of tools from a service
     */
    private fun getToolsList(serviceType: String): MutableList<ToolInfo> {
        // First check built-in adapters
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return try {
                    adapter.modelContextApp.listTools()
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling listTools() on adapter", e)
                    mutableListOf()
                }
            }
        }

        // Check connected services
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.listTools()
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error calling listTools() on service", e)
                    mutableListOf()
                }
            }
        }

        return mutableListOf()
    }

    /**
     * Call a tool on a service
     */
    private fun doCallTool(serviceType: String, toolName: String, jsonArguments: String): MutableList<ContentItem> {
        // First check built-in adapters
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return try {
                    adapter.modelContextApp.callTool(toolName, jsonArguments)
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling tool on adapter", e)
                    mutableListOf(ContentItem.createErrorContent("Tool call error: ${e.message}"))
                }
            }
        }

        // Check connected services
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.callTool(toolName, jsonArguments)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error calling tool on service", e)
                    mutableListOf(ContentItem.createErrorContent("Tool call error: ${e.message}"))
                }
            }
        }

        return mutableListOf(ContentItem.createErrorContent("Service not connected or tool not available"))
    }

    /**
     * Get list of resources from a service
     */
    private fun getResourcesList(serviceType: String): MutableList<ResourceInfo> {
        // First check built-in adapters
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return try {
                    adapter.modelContextApp.listResources()
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling listResources() on adapter", e)
                    mutableListOf()
                }
            }
        }

        // Check connected services
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.listResources()
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error calling listResources() on service", e)
                    mutableListOf()
                }
            }
        }

        return mutableListOf()
    }

    /**
     * Read a resource from a service
     */
    private fun doReadResource(serviceType: String, uri: String): MutableList<ContentItem> {
        // First check built-in adapters
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return try {
                    adapter.modelContextApp.readResource(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading resource from adapter", e)
                    mutableListOf(ContentItem.createErrorContent("Resource read error: ${e.message}"))
                }
            }
        }

        // Check connected services
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.readResource(uri)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error reading resource from service", e)
                    mutableListOf(ContentItem.createErrorContent("Resource read error: ${e.message}"))
                }
            }
        }

        return mutableListOf(ContentItem.createErrorContent("Service not connected or resource not available"))
    }

    /**
     * Check if a service supports a specific capability
     */
    private fun checkServiceCapability(serviceType: String, capability: String): Boolean {
        // First check built-in adapters
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return try {
                    adapter.modelContextApp.hasCapability(capability)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking capability on adapter", e)
                    false
                }
            }
        }

        // Check connected services
        for (service in serviceConnections.values) {
            if (service.serviceInfo.serviceType == serviceType) {
                return try {
                    service.modelContextApp.hasCapability(capability)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error checking capability on service", e)
                    false
                }
            }
        }

        return false
    }

    /**
     * Check if a service type is connected
     */
    private fun isTypeConnected(serviceType: String): Boolean {
        // Check built-in adapters
        if (adapters.values.any { it.serviceInfo.serviceType == serviceType }) {
            return true
        }

        // Check connected services
        return serviceConnections.values.any { it.serviceInfo.serviceType == serviceType }
    }

    /**
     * 서비스 버전 정보 조회
     */
    private fun getVersion(serviceType: String): String {
        // First check built-in adapters
        for (adapter in adapters.values) {
            if (adapter.serviceInfo.serviceType == serviceType) {
                return adapter.modelContextApp.serviceVersion
            }
        }

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

    private data class Adapter(
        val serviceInfo: ServiceInfo,
        val modelContextApp: IModelContextApp
    )
}