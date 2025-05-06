package com.kyungrae.android.datecalculator

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var daysEditText: EditText
    private lateinit var calculateButton: Button
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 요소 초기화
        daysEditText = findViewById(R.id.daysEditText)
        calculateButton = findViewById(R.id.calculateButton)
        resultTextView = findViewById(R.id.resultTextView)

        // 계산 버튼 클릭 이벤트
        calculateButton.setOnClickListener {
            try {
                val days = daysEditText.text.toString().toInt()

                // 직접 날짜 계산 (서비스를 사용하지 않고 UI에서 직접 계산)
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, days)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val resultDate = dateFormat.format(calendar.time)

                resultTextView.text = "계산된 날짜: $resultDate"
            } catch (e: NumberFormatException) {
                resultTextView.text = "유효한 숫자를 입력하세요"
            }
        }
    }
}