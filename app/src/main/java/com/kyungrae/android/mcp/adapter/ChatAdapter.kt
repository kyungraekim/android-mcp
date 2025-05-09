package com.kyungrae.android.mcp.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kyungrae.android.mcp.R
import com.kyungrae.android.mcp.model.ChatMessage
import com.kyungrae.android.mcp.model.MessageType

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageLayout: LinearLayout = itemView.findViewById(R.id.layoutMessage)
        private val messageText: TextView = itemView.findViewById(R.id.textViewMessage)

        fun bind(message: ChatMessage) {
            messageText.text = message.content

            // 메시지 타입에 따른 스타일 설정
            when (message.type) {
                MessageType.USER -> {
                    messageLayout.setBackgroundResource(R.drawable.bg_message_user)
                    val params = messageLayout.layoutParams as FrameLayout.LayoutParams
                    params.gravity = Gravity.END
                    params.marginEnd = 0
                    params.marginStart = 80
                    messageLayout.layoutParams = params
                    messageText.setTextColor(itemView.context.getColor(android.R.color.white))
                }
                MessageType.ASSISTANT -> {
                    messageLayout.setBackgroundResource(R.drawable.bg_message_assistant)
                    val params = messageLayout.layoutParams as FrameLayout.LayoutParams
                    params.gravity = Gravity.START
                    params.marginStart = 0
                    params.marginEnd = 80
                    messageLayout.layoutParams = params
                    messageText.setTextColor(itemView.context.getColor(android.R.color.black))
                }
                MessageType.FUNCTION_CALL -> {
                    messageLayout.setBackgroundResource(R.drawable.bg_message_function)
                    val params = messageLayout.layoutParams as FrameLayout.LayoutParams
                    params.gravity = Gravity.START
                    params.marginStart = 0
                    params.marginEnd = 80
                    messageLayout.layoutParams = params
                    messageText.setTextColor(itemView.context.getColor(android.R.color.black))
                }
                MessageType.FUNCTION_RESULT -> {
                    messageLayout.setBackgroundResource(R.drawable.bg_message_function)
                    val params = messageLayout.layoutParams as FrameLayout.LayoutParams
                    params.gravity = Gravity.START
                    params.marginStart = 0
                    params.marginEnd = 80
                    messageLayout.layoutParams = params
                    messageText.setTextColor(itemView.context.getColor(android.R.color.black))
                }
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}