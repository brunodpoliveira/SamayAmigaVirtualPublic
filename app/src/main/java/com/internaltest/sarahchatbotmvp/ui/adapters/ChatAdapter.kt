package com.internaltest.sarahchatbotmvp.ui.adapters

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.data.Utils
import com.internaltest.sarahchatbotmvp.models.Message
import java.time.LocalDateTime

class ChatAdapter(private val messageList: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var currentUserFontSize: Int = 20

    private val SAMAY_MESSAGE = 1
    private val USER_MESSAGE = 2
    private val DATE_HEADER = 3

    fun updateFontSize(fontSize: Int) {
        currentUserFontSize = fontSize
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].data.isNotEmpty()) {
            DATE_HEADER
        } else if (messageList[position].isReceived) {
            SAMAY_MESSAGE
        } else  {
            USER_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            SAMAY_MESSAGE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_samay_msg, parent, false)
                SamayMessageViewHolder(view)
            }
            USER_MESSAGE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_msg, parent, false)
                UserMessageViewHolder(view)
            }
            DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_data_rv, parent, false)
                DataViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    class SamayMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.tv_message)
        private val messageTimeTextView: TextView = itemView.findViewById(R.id.tv_hr)

        fun bind(message: Message, currentUserFontSize: Int) {
            messageTextView.text = message.message
            messageTextView.textSize = currentUserFontSize.toFloat()
        }
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val messageTextView: TextView = itemView.findViewById(R.id.tv_message)
        private val messageTimeTextView: TextView = itemView.findViewById(R.id.tv_hr)
        private val imgProfile: ImageView = itemView.findViewById(R.id.imageView3)

        fun bind(message: Message, currentUserFontSize: Int) {
            messageTextView.text = message.message
            messageTextView.textSize = currentUserFontSize.toFloat()
            Utils.imageProfile?.let { Glide.with(itemView.context).load(it).into(imgProfile) }
        }
    }

    class DataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.tv_item_data)

        fun bind(message: Message, currentUserFontSize: Int) {
            messageTextView.text = message.data

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            SAMAY_MESSAGE -> {
                val samayMessageHolder = holder as SamayMessageViewHolder
                val textMessage = messageList[position]
                samayMessageHolder.bind(textMessage, currentUserFontSize)

            }
            USER_MESSAGE -> {
                val userMessageHolder = holder as UserMessageViewHolder
                val textMessage = messageList[position]
                userMessageHolder.bind(textMessage, currentUserFontSize)

            }
            DATE_HEADER -> {
                val dataViewHolder = holder as DataViewHolder
                val textMessage = messageList[position]
                dataViewHolder.bind(textMessage, currentUserFontSize)
            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
}