package com.internaltest.sarahchatbotmvp.models

import com.google.firebase.firestore.PropertyName

data class Message(
    val message: String = "",
    @get:PropertyName("received")
    @set:PropertyName("received")
    var isReceived: Boolean = false
) {
    //não deletar
    constructor() : this("", false)
}