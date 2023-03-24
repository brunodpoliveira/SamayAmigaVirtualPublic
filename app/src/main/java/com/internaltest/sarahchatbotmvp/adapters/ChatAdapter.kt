package com.internaltest.sarahchatbotmvp.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.adapters.ChatAdapter.MyViewHolder
import com.internaltest.sarahchatbotmvp.models.Message

class ChatAdapter(private val messageList: List<Message>, private val activity: Activity) :
    RecyclerView.Adapter<MyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view =
            LayoutInflater.from(activity).inflate(R.layout.adapter_message_one, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val message = messageList[position].message
        val isReceived = messageList[position].isReceived

        //arrastar
        holder.messageReceive.setTextIsSelectable(false)
        holder.messageReceive.measure(-1, -1)
        holder.messageReceive.setTextIsSelectable(true)
        if (isReceived) {
            holder.messageReceive.visibility = View.VISIBLE
            holder.messageSend.visibility = View.GONE
            holder.imageReceive.visibility = View.GONE
            holder.imageReceiveScroll.visibility = View.GONE
            holder.messageReceive.text = message
            holder.messageReceive.isClickable = true
        } else {
            holder.messageSend.visibility = View.VISIBLE
            holder.messageReceive.visibility = View.GONE
            holder.imageReceive.visibility = View.GONE
            holder.imageReceiveScroll.visibility = View.GONE
            holder.messageSend.text = message
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var messageSend: TextView
        var messageReceive: TextView
        var imageReceive: ImageView
        var imageReceiveScroll: HorizontalScrollView
        private var imageReceiveScroll1: ImageView
        private var imageReceiveScroll2: ImageView
        private var imageReceiveScroll3: ImageView
        private var imageReceiveScroll4: ImageView
        private var imageReceiveScroll5: ImageView
        private var imageReceiveScroll6: ImageView
        private var imageReceiveScroll7: ImageView
        private var imageReceiveScroll8: ImageView

        init {
            messageSend = itemView.findViewById(R.id.message_send)
            messageReceive = itemView.findViewById(R.id.message_receive)
            imageReceive = itemView.findViewById(R.id.image_receive)
            imageReceiveScroll = itemView.findViewById(R.id.image_receive_scroll)
            imageReceiveScroll1 = itemView.findViewById(R.id.scrollimage1)
            imageReceiveScroll2 = itemView.findViewById(R.id.scrollimage2)
            imageReceiveScroll3 = itemView.findViewById(R.id.scrollimage3)
            imageReceiveScroll4 = itemView.findViewById(R.id.scrollimage4)
            imageReceiveScroll5 = itemView.findViewById(R.id.scrollimage5)
            imageReceiveScroll6 = itemView.findViewById(R.id.scrollimage6)
            imageReceiveScroll7 = itemView.findViewById(R.id.scrollimage7)
            imageReceiveScroll8 = itemView.findViewById(R.id.scrollimage8)
        }
    }
}