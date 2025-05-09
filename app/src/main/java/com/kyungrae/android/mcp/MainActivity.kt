package com.kyungrae.android.mcp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kyungrae.android.mcp.adapter.ChatAdapter
import com.kyungrae.android.mcp.api.ApiClient
import com.kyungrae.android.mcp.databinding.ActivityMainBinding
import com.kyungrae.android.mcp.function.FunctionExecutor
import com.kyungrae.android.mcp.model.ChatCompletionRequest
import com.kyungrae.android.mcp.model.ChatMessage
import com.kyungrae.android.mcp.model.Message
import com.kyungrae.android.mcp.model.MessageType
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val apiMessages = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSendButton()

        // 시스템 메시지 추가
        apiMessages.add(
            Message(
                role = "system",
                content = "당신은 사용자의 자연어 입력을 분석하여 적절한 함수를 호출하거나 일반 텍스트로 응답하는 어시스턴트입니다. " +
                        "함수 호출이 필요한 입력이면 반드시 함수를 호출하고, 그렇지 않으면 일반 텍스트로 응답하세요."
            )
        )
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.editTextMessage.text.clear()
            }
        }
    }

    private fun sendMessage(message: String) {
        // 사용자 메시지 추가
        val userMessage = ChatMessage(
            content = message,
            type = MessageType.USER
        )
        chatMessages.add(userMessage)
        updateChatList()

        // API 메시지 목록에 추가
        apiMessages.add(Message(role = "user", content = message))

        // API 호출
        sendToChatGPT()
    }

    private fun sendToChatGPT() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val request = ChatCompletionRequest(
                    model = "gpt-4", // Azure에서는 이 필드가 무시되지만 필수 필드이므로 유지
                    messages = apiMessages,
                    functions = ApiClient.functions,
                    function_call = "auto"
                )

                val response = ApiClient.azureOpenAiApi.createChatCompletion(
                    deploymentId = ApiClient.getDeploymentName(),
                    apiVersion = ApiClient.getApiVersion(),
                    apiKey = ApiClient.getApiKeyHeader(),
                    request = request
                )

                // 응답 처리
                val assistantMessage = response.choices.firstOrNull()?.message
                if (assistantMessage != null) {
                    apiMessages.add(assistantMessage)

                    if (assistantMessage.function_call != null) {
                        // 함수 호출 메시지 추가
                        val functionCallMessage = ChatMessage(
                            content = "함수 호출: ${assistantMessage.function_call.name}\n인자: ${assistantMessage.function_call.arguments}",
                            type = MessageType.FUNCTION_CALL
                        )
                        chatMessages.add(functionCallMessage)
                        updateChatList()

                        // 함수 실행
                        val functionResult =
                            FunctionExecutor.executeFunction(assistantMessage.function_call)

                        // 함수 결과 메시지 추가
                        val functionResultMessage = ChatMessage(
                            content = "함수 결과: $functionResult",
                            type = MessageType.FUNCTION_RESULT
                        )
                        chatMessages.add(functionResultMessage)
                        updateChatList()

                        // API 메시지에 함수 결과 추가
                        apiMessages.add(
                            Message(
                                role = "function",
                                name = assistantMessage.function_call.name,
                                content = functionResult
                            )
                        )

                        // 결과를 바탕으로 다시 API 호출
                        sendToChatGPT()
                    } else {
                        // 일반 응답 메시지 추가
                        val assistantChatMessage = ChatMessage(
                            content = assistantMessage.content ?: "응답 없음",
                            type = MessageType.ASSISTANT
                        )
                        chatMessages.add(assistantChatMessage)
                        updateChatList()
                    }
                }
            } catch (e: Exception) {
                // 오류 처리
                val errorMessage = ChatMessage(
                    content = "오류 발생: ${e.message}",
                    type = MessageType.ASSISTANT
                )
                chatMessages.add(errorMessage)
                updateChatList()

                Toast.makeText(this@MainActivity, "API 호출 오류: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateChatList() {
        chatAdapter.submitList(chatMessages.toList())
        binding.recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }
}