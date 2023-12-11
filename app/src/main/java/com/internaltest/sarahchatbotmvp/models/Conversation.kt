package com.internaltest.sarahchatbotmvp.models

data class Conversation(
    val name: String = "",
    var messages: List<Message> = listOf(),
    var id: String = "",
    var fileName: String? = null,
    var dataCriada: String? = null,
    var horaCriada: String? = null
) {
    //não deletar
    constructor() : this("", listOf(), "", null, null, null)
}

