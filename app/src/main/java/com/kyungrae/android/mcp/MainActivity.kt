package com.kyungrae.android.mcp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kyungrae.android.mcp.host.LanguageModelInterface
import com.kyungrae.android.mcp.host.McpHost
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var mcpHost: McpHost
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var responseTextView: TextView
    private lateinit var progressView: View

    // Required permissions for calendar, contacts, etc.
    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE
    )

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initializeMcpHost()
        } else {
            Toast.makeText(
                this,
                "Some permissions were denied. Functionality may be limited.",
                Toast.LENGTH_LONG
            ).show()

            // Initialize anyway, but some features won't work
            initializeMcpHost()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        responseTextView = findViewById(R.id.responseTextView)
        progressView = findViewById(R.id.progressView)

        // Set up send button
        sendButton.setOnClickListener {
            val userInput = inputEditText.text.toString().trim()
            if (userInput.isNotEmpty()) {
                processUserInput(userInput)
            }
        }

        // Check and request permissions
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            // All permissions already granted
            initializeMcpHost()
        }
    }

    private fun initializeMcpHost() {
        progressView.visibility = View.VISIBLE

        // Create the MCP Host
        mcpHost = McpHost(applicationContext)

        // Create a simple LLM implementation
        val llm = SampleLanguageModel()

        // Initialize the host
        lifecycleScope.launch {
            try {
                mcpHost.initialize(llm)
                runOnUiThread {
                    progressView.visibility = View.GONE
                    sendButton.isEnabled = true
                    responseTextView.text = "MCP Agent ready! Ask me anything..."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressView.visibility = View.GONE
                    responseTextView.text = "Error initializing MCP Agent: ${e.message}"
                }
            }
        }
    }

    private fun processUserInput(userInput: String) {
        inputEditText.text.clear()
        progressView.visibility = View.VISIBLE
        sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = mcpHost.processUserInput(userInput)
                runOnUiThread {
                    progressView.visibility = View.GONE
                    sendButton.isEnabled = true
                    responseTextView.text = response
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressView.visibility = View.GONE
                    sendButton.isEnabled = true
                    responseTextView.text = "Error: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shutdown the MCP host
        lifecycleScope.launch {
            mcpHost.shutdown()
        }
    }
}


class SampleLanguageModel : LanguageModelInterface {

}