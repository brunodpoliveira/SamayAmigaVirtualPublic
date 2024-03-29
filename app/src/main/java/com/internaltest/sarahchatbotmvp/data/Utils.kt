package com.internaltest.sarahchatbotmvp.data

import android.net.Uri
import com.internaltest.sarahchatbotmvp.models.Message
import com.theokanning.openai.completion.chat.ChatMessage

object Utils {
    var imageProfile : Uri? = null
    var messageList: MutableList<Message> = ArrayList()
    var userId : String? = null
    val msgs: MutableList<ChatMessage> = ArrayList()
    var userName : String? = null
    var userEmail : String? = null
}