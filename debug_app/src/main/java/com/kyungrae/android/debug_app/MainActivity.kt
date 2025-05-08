package com.kyungrae.android.debug_app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kyungrae.android.modelcontext.IModelContextService
import com.kyungrae.android.modelcontext.IServiceDiscoveryCallback
import com.kyungrae.android.modelcontext.ResourceInfo
import com.kyungrae.android.modelcontext.ServiceInfo
import com.kyungrae.android.modelcontext.ToolInfo
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val TAG = "CalculatorClient"

    // UI 요소
    private lateinit var spinnerServiceType: Spinner
    private lateinit var btnRefreshServices: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var etValue: EditText
    private lateinit var btnCalculate: Button
    private lateinit var btnSchedule: Button
    private lateinit var tvServiceVersion: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvDebugInfo: TextView

    // 현재 결과
    private var currentResult: String = ""

    // 서비스 관리자 인터페이스
    private var serviceManager: IModelContextService? = null

    // 현재 선택된 서비스 정보
    private var selectedServiceInfo: ServiceInfo? = null
    private var selectedServiceType: String = "date" // 기본값

    // Available tools for the selected service
    private var availableTools = mutableListOf<ToolInfo>()

    // Available resources for the selected service
    private var availableResources = mutableListOf<ResourceInfo>()

    // 발견된 서비스 목록
    private val discoveredServices = mutableListOf<ServiceInfo>()

    // 서비스 관리자 연결 상태
    private var isServiceManagerConnected = false

    // 서비스 관리자 연결 객체
    private val serviceManagerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service manager connected")
            serviceManager = IModelContextService.Stub.asInterface(service)
            isServiceManagerConnected = true

            // 서비스 목록 새로고침
            refreshAvailableServices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service manager disconnected")
            serviceManager = null
            isServiceManagerConnected = false

            // UI 업데이트
            updateUIForDisconnectedManager()
        }
    }

    // 서비스 발견 콜백
    private val discoveryCallback = object : IServiceDiscoveryCallback.Stub() {
        override fun onServicesDiscovered(services: MutableList<ServiceInfo>) {
            Log.d(TAG, "Services discovered: ${services.size}")

            // UI 스레드에서 UI 업데이트
            runOnUiThread {
                discoveredServices.clear()
                discoveredServices.addAll(services)

                updateSelectedService()
                updateServiceStatus()
                updateDebugInfo()

                btnRefreshServices.isEnabled = true

                Toast.makeText(this@MainActivity, "${services.size}개의 서비스가 발견되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "MainActivity created")

        // UI 요소 초기화
        initializeViews()

        // 서비스 관리자에 연결
        connectToServiceManager()

        // 이벤트 리스너 설정
        setupEventListeners()
    }

    private fun initializeViews() {
        spinnerServiceType = findViewById(R.id.spinnerServiceType)
        btnRefreshServices = findViewById(R.id.btnRefreshServices)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        etValue = findViewById(R.id.etValue)
        btnCalculate = findViewById(R.id.btnCalculate)
        btnSchedule = findViewById(R.id.btnSchedule)
        tvServiceVersion = findViewById(R.id.tvServiceVersion)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvResult = findViewById(R.id.tvResult)
        tvDebugInfo = findViewById(R.id.tvDebugInfo)

        // 기본 상태 설정
        btnRefreshServices.isEnabled = false
        btnConnect.isEnabled = false
        btnDisconnect.isEnabled = false
        btnCalculate.isEnabled = false
        btnSchedule.isEnabled = false

        // 힌트 텍스트 설정
        updateInputHint()

        // 스피너 어댑터 설정
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            listOf("날짜 계산 (date)", "시간 계산 (time)", "캘린더 (schedule)")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerServiceType.adapter = adapter
    }

    private fun connectToServiceManager() {
        Log.d(TAG, "Connecting to service manager...")

        val intent = Intent(this, ModelContextServiceImpl::class.java)
        val bound = bindService(intent, serviceManagerConnection, BIND_AUTO_CREATE)
        if (!bound) {
            Log.e(TAG, "Failed to bind to service manager")
            Toast.makeText(this, "서비스 관리자 연결에 실패했습니다. 앱이 설치되어 있는지 확인하세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupEventListeners() {
        // 서비스 유형 선택 이벤트
        spinnerServiceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // 선택된 서비스 유형에 따라 UI 업데이트
                selectedServiceType = when (position) {
                    0 -> "date"
                    1 -> "time"
                    2 -> "schedule"
                    else -> "date"
                }
                Log.d(TAG, "Selected service type: $selectedServiceType")
                updateSelectedService()
                updateInputHint()
                updateServiceStatus()

                // Fetch tools and resources if connected
                if (isServiceTypeConnected()) {
                    fetchToolsAndResources()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 아무것도 하지 않음
            }
        }

        // 새로고침 버튼
        btnRefreshServices.setOnClickListener {
            refreshAvailableServices()
        }

        // 연결 버튼
        btnConnect.setOnClickListener {
            connectToSelectedService()
        }

        // 연결 해제 버튼
        btnDisconnect.setOnClickListener {
            disconnectFromSelectedService()
        }

        // 계산 버튼
        btnCalculate.setOnClickListener {
            if (selectedServiceType == "schedule") {
                // For calendar, show/add events
                viewUpcomingEvents()
            } else {
                // For date/time calculators
                performCalculation()
            }
        }

        // 스케줄 버튼
        btnSchedule.setOnClickListener {
            addCalendarEvent()
        }
    }

    private fun refreshAvailableServices() {
        Log.d(TAG, "Refreshing available services...")

        if (!isServiceManagerConnected) {
            Log.e(TAG, "Service manager not connected")
            Toast.makeText(this, "서비스 관리자에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // UI 업데이트 (진행 중 상태)
        tvDebugInfo.text = "서비스 검색 중..."
        btnRefreshServices.isEnabled = false

        try {
            // 서비스 관리자에 검색 요청
            serviceManager?.discoverServices(discoveryCallback)
        } catch (e: RemoteException) {
            Log.e(TAG, "Error calling discoverServices()", e)
            Toast.makeText(this, "서비스 검색 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            btnRefreshServices.isEnabled = true
        }
    }

    private fun updateSelectedService() {
        if (!isServiceManagerConnected) return

        try {
            // 현재 선택된 유형의 서비스 목록 가져오기
            val services = serviceManager?.getServicesByType(selectedServiceType)
            selectedServiceInfo = if (services != null && services.isNotEmpty()) services[0] else null

            Log.d(TAG, "Selected service type: $selectedServiceType, found: ${selectedServiceInfo != null}")

            // UI 업데이트
            updateServiceStatus()
            updateDebugInfo()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error in updateSelectedService()", e)
        }
    }

    private fun updateInputHint() {
        // 선택된 서비스 유형에 따라 입력 힌트 변경
        when (selectedServiceType) {
            "date" -> {
                etValue.hint = "일수 입력 (예: 30)"
                btnSchedule.isEnabled = false
                btnCalculate.text = "날짜 계산"
            }
            "time" -> {
                etValue.hint = "시간 입력 (예: 24)"
                btnSchedule.isEnabled = false
                btnCalculate.text = "시간 계산"
            }
            "schedule" -> {
                etValue.hint = "향후 일수 입력 (예: 7)"
                btnSchedule.isEnabled = isServiceTypeConnected()
                btnCalculate.text = "이벤트 보기"
            }
        }
    }

    private fun updateServiceStatus() {
        if (!isServiceManagerConnected) {
            tvServiceStatus.text = "서비스 관리자 상태: 연결되지 않음"
            tvServiceVersion.text = "서비스 버전: 알 수 없음"
            btnConnect.isEnabled = false
            btnDisconnect.isEnabled = false
            btnCalculate.isEnabled = false
            btnSchedule.isEnabled = false
            return
        }

        try {
            val isServiceAvailable = selectedServiceInfo != null
            val isServiceConnected = isServiceTypeConnected()

            // 서비스 상태 표시
            if (!isServiceAvailable) {
                tvServiceStatus.text = "서비스 상태: 설치되지 않음"
                tvServiceVersion.text = "서비스 버전: 알 수 없음"
            } else if (isServiceConnected) {
                tvServiceStatus.text = "서비스 상태: 연결됨"
                // 버전 정보 가져오기
                val version = serviceManager?.getServiceVersion(selectedServiceType) ?: "알 수 없음"
                tvServiceVersion.text = "서비스 버전: $version"
            } else {
                tvServiceStatus.text = "서비스 상태: 연결되지 않음"
                tvServiceVersion.text = "서비스 버전: 알 수 없음"
            }

            // 버튼 활성화 상태 업데이트
            btnConnect.isEnabled = isServiceAvailable && !isServiceConnected
            btnDisconnect.isEnabled = isServiceConnected
            btnCalculate.isEnabled = isServiceConnected
            btnSchedule.isEnabled = isServiceConnected && selectedServiceType == "schedule"

        } catch (e: RemoteException) {
            Log.e(TAG, "Error in updateServiceStatus()", e)
            tvServiceStatus.text = "서비스 상태: 오류"
            tvServiceVersion.text = "서비스 버전: 오류"
        }
    }

    private fun isServiceTypeConnected(): Boolean {
        return try {
            serviceManager?.isServiceTypeConnected(selectedServiceType) == true
        } catch (e: RemoteException) {
            Log.e(TAG, "Error checking if service is connected", e)
            false
        }
    }

    private fun fetchToolsAndResources() {
        // Fetch available tools
        try {
            availableTools = serviceManager?.listTools(selectedServiceType) ?: mutableListOf()
            availableResources = serviceManager?.listResources(selectedServiceType) ?: mutableListOf()

            val serviceDebugInfo = StringBuilder()

            // Add tools information
            serviceDebugInfo.append("\nAvailable Tools (${availableTools.size}):\n")
            availableTools.forEachIndexed { index, tool ->
                serviceDebugInfo.append("${index + 1}. ${tool.name}: ${tool.description}\n")
            }

            // Add resources information
            serviceDebugInfo.append("\nAvailable Resources (${availableResources.size}):\n")
            availableResources.forEachIndexed { index, resource ->
                serviceDebugInfo.append("${index + 1}. ${resource.name}: ${resource.uri}\n")
            }

            // Add capability information
            val capabilities = listOf("tools", "resources")
            serviceDebugInfo.append("\nCapabilities:\n")
            capabilities.forEach { capability ->
                val hasCapability = serviceManager?.serviceHasCapability(selectedServiceType, capability) ?: false
                serviceDebugInfo.append("- $capability: ${if (hasCapability) "Supported" else "Not supported"}\n")
            }

            // Add to debug info
            tvDebugInfo.text = tvDebugInfo.text.toString() + serviceDebugInfo.toString()

        } catch (e: RemoteException) {
            Log.e(TAG, "Error fetching tools and resources", e)
        }
    }

    private fun shortenPackageName(packageName: String): String {
        val splits = packageName.split('.')
        val len = splits.size
        return splits.mapIndexed { index, part ->
                if (index == len - 1) {
                    part
                } else {
                    part.firstOrNull()?.toString() ?: ""
                }
            }
            .joinToString(".")
    }

    private fun updateDebugInfo() {
        // 디버그 정보 업데이트
        val debugInfo = StringBuilder()

        if (!isServiceManagerConnected) {
            debugInfo.append("서비스 관리자에 연결되어 있지 않습니다.\n")
        } else {
            debugInfo.append("발견된 서비스: ${discoveredServices.size}개\n")

            for ((index, service) in discoveredServices.withIndex()) {
                debugInfo.append(
                    "${index + 1}. 타입: ${service.serviceType}, 패키지: ${
                        shortenPackageName(
                            service.packageName
                        )
                    }\n"
                )
            }

            debugInfo.append("\n선택된 서비스 타입: $selectedServiceType\n")

            if (selectedServiceInfo != null) {
                debugInfo.append("선택된 서비스: ${selectedServiceInfo?.packageName}/${selectedServiceInfo?.className}\n")
            } else {
                debugInfo.append("선택된 서비스: 없음\n")
            }
        }

        tvDebugInfo.text = debugInfo.toString()
    }

    private fun updateUIForDisconnectedManager() {
        // 서비스 관리자 연결 해제 시 UI 업데이트
        tvServiceStatus.text = "서비스 관리자 상태: 연결되지 않음"
        tvServiceVersion.text = "서비스 버전: 알 수 없음"
        btnRefreshServices.isEnabled = false
        btnConnect.isEnabled = false
        btnDisconnect.isEnabled = false
        btnCalculate.isEnabled = false
        btnSchedule.isEnabled = false

        updateDebugInfo()

        Toast.makeText(this, "서비스 관리자와의 연결이 끊어졌습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun connectToSelectedService() {
        val serviceInfo = selectedServiceInfo ?: return

        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스 관리자에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Connecting to service: ${serviceInfo.packageName}/${serviceInfo.className}")

        try {
            // 서비스 관리자를 통해 서비스 연결
            val result = serviceManager?.connectToService(serviceInfo) == true

            if (result) {
                Toast.makeText(this, "서비스에 연결을 요청했습니다.", Toast.LENGTH_SHORT).show()

                // 잠시 후 상태 업데이트 (연결 완료 대기)
                Handler(mainLooper).postDelayed({
                    updateServiceStatus()
                    updateDebugInfo()
                    fetchToolsAndResources()
                }, 500)
            } else {
                Toast.makeText(this, "서비스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Error connecting to service", e)
            Toast.makeText(this, "서비스 연결 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectFromSelectedService() {
        val serviceInfo = selectedServiceInfo ?: return

        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Disconnecting from service: ${serviceInfo.packageName}/${serviceInfo.className}")

        try {
            // 서비스 관리자를 통해 서비스 연결 해제
            serviceManager?.disconnectFromService(serviceInfo)

            // 상태 업데이트
            updateServiceStatus()
            updateDebugInfo()

            // Clear tools and resources
            availableTools.clear()
            availableResources.clear()

            Toast.makeText(this, "서비스 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error disconnecting from service", e)
            Toast.makeText(this, "서비스 연결 해제 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performCalculation() {
        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 서비스가 연결되었는지 확인
            val isServiceConnected = serviceManager?.isServiceTypeConnected(selectedServiceType) ?: false

            if (!isServiceConnected) {
                Toast.makeText(this, "먼저 서비스에 연결하세요.", Toast.LENGTH_SHORT).show()
                return
            }

            // 입력값 가져오기
            val valueStr = etValue.text.toString()
            if (valueStr.isEmpty()) {
                Toast.makeText(this, "값을 입력하세요.", Toast.LENGTH_SHORT).show()
                return
            }

            // Call service via MCP-like tool interface (using JSON for arguments)
            val jsonArgs = JSONObject().apply {
                when (selectedServiceType) {
                    "date" -> put("days", valueStr.toInt())
                    "time" -> put("hours", valueStr.toInt())
                    else -> put("value", valueStr)
                }
            }.toString()

            val toolName = when (selectedServiceType) {
                "date" -> "add_days"
                "time" -> "add_hours"
                else -> "unknown"
            }

            // 서비스를 통해 tool 호출
            val resultContent = serviceManager?.callTool(selectedServiceType, toolName, jsonArgs)

            if (resultContent != null && resultContent.isNotEmpty()) {
                val firstContent = resultContent[0]
                currentResult = firstContent.content

                // Display result
                if (firstContent.isError) {
                    tvResult.text = "Error: ${firstContent.content}"
                } else {
                    tvResult.text = when (selectedServiceType) {
                        "date" -> "계산된 날짜: $currentResult"
                        "time" -> "계산된 시간: $currentResult"
                        else -> currentResult
                    }
                }
            } else {
                // Fallback to legacy method
                val result = serviceManager?.calculate(selectedServiceType, valueStr)
                currentResult = result ?: ""

                // Display result
                tvResult.text = when (selectedServiceType) {
                    "date" -> "계산된 날짜: $result"
                    "time" -> "계산된 시간: $result"
                    else -> "$result"
                }
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "유효한 숫자를 입력하세요.", Toast.LENGTH_SHORT).show()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error calling service", e)
            Toast.makeText(this, "서비스 호출 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun viewUpcomingEvents() {
        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Check if service is connected
            val isServiceConnected = serviceManager?.isServiceTypeConnected(selectedServiceType) ?: false

            if (!isServiceConnected) {
                Toast.makeText(this, "먼저 서비스에 연결하세요.", Toast.LENGTH_SHORT).show()
                return
            }

            // Get days to look ahead
            val days = etValue.text.toString().ifEmpty { "7" }.toInt()

            // Call query_events tool
            val jsonArgs = JSONObject().apply {
                put("days", days)
            }.toString()

            // Call tool via service manager
            val resultContent = serviceManager?.callTool(selectedServiceType, "query_events", jsonArgs)

            if (resultContent != null && resultContent.isNotEmpty()) {
                val firstContent = resultContent[0]

                if (firstContent.isError) {
                    tvResult.text = "Error: ${firstContent.content}"
                } else {
                    // Parse JSON response
                    try {
                        val eventsJson = JSONObject(firstContent.content)
                        val eventsArray = eventsJson.getJSONArray("events")

                        val eventsText = StringBuilder()
                        eventsText.append("Upcoming events (${eventsJson.optString("period", "")}):\n\n")

                        for (i in 0 until eventsArray.length()) {
                            val event = eventsArray.getJSONObject(i)
                            eventsText.append("${event.getString("title")}\n")
                            eventsText.append("Date: ${event.getString("date")}\n")
                            eventsText.append("Time: ${event.getString("startTime")}\n")
                            eventsText.append("Duration: ${event.getString("duration")}\n")
                            if (i < eventsArray.length() - 1) {
                                eventsText.append("\n")
                            }
                        }

                        tvResult.text = eventsText.toString()
                    } catch (e: Exception) {
                        tvResult.text = "Error parsing events: ${e.message}"
                    }
                }
            } else {
                tvResult.text = "No events found or service error"
            }

        } catch (e: NumberFormatException) {
            Toast.makeText(this, "유효한 숫자를 입력하세요.", Toast.LENGTH_SHORT).show()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error calling service", e)
            Toast.makeText(this, "서비스 호출 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCalendarEvent() {
        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentResult.isBlank()) {
            Toast.makeText(this, "먼저 날짜/시간을 계산해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Parse the previous calculation result
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = dateFormat.parse(currentResult)

            if (date == null) {
                Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
                return
            }

            // Format for the add_event tool
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val jsonArgs = JSONObject().apply {
                put("title", "Event from MCP Demo")
                put("location", "MCP Demo Location")
                put("day", dayFormat.format(date))
                put("startTime", timeFormat.format(date))
                put("durationMinutes", 60)
            }.toString()

            // Call add_event tool
            val resultContent = serviceManager?.callTool("schedule", "add_event", jsonArgs)

            if (resultContent != null && resultContent.isNotEmpty()) {
                val firstContent = resultContent[0]

                if (firstContent.isError) {
                    Toast.makeText(this, "Error: ${firstContent.content}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, firstContent.content, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback to legacy method
                val result = serviceManager?.calculate("schedule", jsonArgs)
                Toast.makeText(this, result ?: "Done", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error adding calendar event", e)
            Toast.makeText(this, "일정 추가 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 서비스 관리자 연결 해제
        if (isServiceManagerConnected) {
            unbindService(serviceManagerConnection)
            isServiceManagerConnected = false
        }
    }
}