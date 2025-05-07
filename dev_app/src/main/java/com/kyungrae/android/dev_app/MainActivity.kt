package com.kyungrae.android.dev_app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.kyungrae.android.modelcontext.IModelContextServiceOld
import com.kyungrae.android.modelcontext.IServiceDiscoveryCallback
import com.kyungrae.android.modelcontext.ServiceInfo
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    private var current: String = ""
    // 서비스 관리자 인터페이스
    private var serviceManager: IModelContextServiceOld? = null

    // 현재 선택된 서비스 정보
    private var selectedServiceInfo: ServiceInfo? = null
    private var selectedServiceType: String = "date" // 기본값

    // 발견된 서비스 목록
    private val discoveredServices = mutableListOf<ServiceInfo>()

    // 서비스 관리자 연결 상태
    private var isServiceManagerConnected = false

    // 서비스 관리자 연결 객체
    private val serviceManagerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service manager connected")
            serviceManager = IModelContextServiceOld.Stub.asInterface(service)
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
            listOf("날짜 계산 (date)", "시간 계산 (time)")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerServiceType.adapter = adapter
    }

    private fun connectToServiceManager() {
        Log.d(TAG, "Connecting to service manager...")

        val intent = Intent("com.kyungrae.android.modelcontext.MODELCONTEXT_SERVICE")
        intent.setPackage("com.kyungrae.android.modelcontext") // 명시적 인텐트

        val bound = bindService(intent, serviceManagerConnection, Context.BIND_AUTO_CREATE)
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
                selectedServiceType = if (position == 0) "date" else "time"
                Log.d(TAG, "Selected service type: $selectedServiceType")
                updateSelectedService()
                updateInputHint()
                updateServiceStatus()
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
            performCalculation()
        }

        // 스케줄 버튼
        btnSchedule.setOnClickListener {
            addSchedule()
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
        if (selectedServiceType == "date") {
            etValue.hint = "일수 입력 (예: 30)"
        } else {
            etValue.hint = "시간 입력 (예: 24)"
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
            val isServiceConnected = serviceManager?.isServiceTypeConnected(selectedServiceType) ?: false

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
            btnSchedule.isEnabled = isServiceConnected

        } catch (e: RemoteException) {
            Log.e(TAG, "Error in updateServiceStatus()", e)
            tvServiceStatus.text = "서비스 상태: 오류"
            tvServiceVersion.text = "서비스 버전: 오류"
        }
    }

    private fun updateDebugInfo() {
        // 디버그 정보 업데이트
        val debugInfo = StringBuilder()

        if (!isServiceManagerConnected) {
            debugInfo.append("서비스 관리자에 연결되어 있지 않습니다.\n")
        } else {
            debugInfo.append("발견된 서비스: ${discoveredServices.size}개\n")

            for ((index, service) in discoveredServices.withIndex()) {
                debugInfo.append("${index + 1}. 타입: ${service.serviceType}, 패키지: ${service.packageName}\n")
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
            val result = serviceManager?.connectToService(serviceInfo) ?: false

            if (result) {
                Toast.makeText(this, "서비스에 연결을 요청했습니다.", Toast.LENGTH_SHORT).show()

                // 잠시 후 상태 업데이트 (연결 완료 대기)
                Handler(mainLooper).postDelayed({
                    updateServiceStatus()
                    updateDebugInfo()
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
            Toast.makeText(this, "서비스 관리자에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Disconnecting from service: ${serviceInfo.packageName}/${serviceInfo.className}")

        try {
            // 서비스 관리자를 통해 서비스 연결 해제
            serviceManager?.disconnectFromService(serviceInfo)

            // 상태 업데이트
            updateServiceStatus()
            updateDebugInfo()

            Toast.makeText(this, "서비스 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error disconnecting from service", e)
            Toast.makeText(this, "서비스 연결 해제 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performCalculation() {
        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스 관리자에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
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

            // val value = valueStr.toInt()

            // 서비스 관리자를 통해 계산 요청
            val result = serviceManager?.calculate(selectedServiceType, valueStr)

            // 결과 표시
            if (selectedServiceType == "date") {
                tvResult.text = "계산된 날짜: $result"
            } else {
                tvResult.text = "계산된 시간: $result"
            }
            if (result != null) {
                current = result
            }

        } catch (e: NumberFormatException) {
            Toast.makeText(this, "유효한 숫자를 입력하세요.", Toast.LENGTH_SHORT).show()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error calling service", e)
            Toast.makeText(this, "서비스 호출 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSchedule() {
        if (!isServiceManagerConnected) {
            Toast.makeText(this, "서비스 관리자에 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (current.isBlank()) {
            Toast.makeText(this, "먼저 날짜/시간을 계산해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime = LocalDateTime.parse(current, inputFormatter)

        val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dayString = localDateTime.format(dayFormatter)

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val timeString = localDateTime.format(timeFormatter)

        val jsonObject = JSONObject()
        jsonObject.put("title", "Sample Schedule")
        jsonObject.put("location", "Sample Location")
        jsonObject.put("day", dayString)
        jsonObject.put("startTime", timeString)
        jsonObject.put("durationMinutes", 60)
        val jsonString = jsonObject.toString()

        val result = serviceManager?.calculate("schedule", jsonString)
        Toast.makeText(this, result?:"Done", Toast.LENGTH_SHORT).show()
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