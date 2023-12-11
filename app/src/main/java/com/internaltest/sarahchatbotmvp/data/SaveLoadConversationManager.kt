package com.internaltest.sarahchatbotmvp.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.api.client.util.DateTime
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.internaltest.sarahchatbotmvp.auth.SignIn
import com.internaltest.sarahchatbotmvp.models.Conversation
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import com.internaltest.sarahchatbotmvp.utils.DialogUtils
import com.internaltest.sarahchatbotmvp.utils.FirebaseInstance
import com.theokanning.openai.completion.chat.ChatMessage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalTime

class SaveLoadConversationManager(
    private val mainActivity: MainActivity,
    private val context: Context,

) {
    companion object {
        const val REQUEST_CODE_PERMISSIONS = 200
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For API level 29 and higher, handle permissions using requestPermissions()
            val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val deniedPermissions = permissions.filter { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (deniedPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    mainActivity,
                    deniedPermissions,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        } else {
            // For API level 28 and lower, handle storage permission directly
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            val permissionGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                // Show explanation if needed
                if (ActivityCompat.shouldShowRequestPermissionRationale(mainActivity, permission)) {
                    // Show an explanation to the user asynchronously if needed
                    // e.g., using a dialog or a snackbar
                } else {
                    // Request the permission directly
                    ActivityCompat.requestPermissions(
                        mainActivity,
                        arrayOf(permission),
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            }
        }
    }

    fun saveConversationOnClick(userName: String, userId: String, exit : Boolean = false) {
        checkAndRequestPermissions()

        val conversation = Conversation(userName, mainActivity.messageList, userId)
        if (userName.isBlank()) return
        DialogUtils.showSaveConversationDialog(
            context,
            conversation
        ) { fileName, savedConversation, isRemote ->
            saveConversation(fileName, savedConversation, isRemote, exit)
        }
    }

    fun loadConversationOnClick(isFromNuvem: Boolean) {
        if (isFromNuvem) {
            //carregar do firebase
            val firebaseDatabase = FirebaseInstance.firebaseDatabase
            val conversationsRef = firebaseDatabase.getReference("conversations/${mainActivity.userId.toString()}")
            conversationsRef.get().addOnSuccessListener { dataSnapshot ->
                if (dataSnapshot.exists()) {
                    val conversationList = dataSnapshot.children.map { it.getValue(Conversation::class.java)!! }
                    showConversationListDialog(conversationList)
                } else {
                    Toast.makeText(context, "Nenhuma conversa encontrada", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Load from internal storage
            val folderName = "conversas_samay_vr"
            val folderPath = File(context.filesDir, folderName)

            // Get a list of all .json files in the folder
            val files = folderPath.listFiles { _, name -> name.endsWith(".json") }

            val fileNames = files?.map { it.nameWithoutExtension }

            if (fileNames.isNullOrEmpty()) {
                Toast.makeText(context, "Nenhuma conversa encontrada", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    //TODO colocar essa função no DialogUtils
    private fun showConversationListDialog(conversationList: List<Conversation>) {
        val conversationNames = conversationList.map { it.fileName }.toTypedArray()

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Escolha uma conversa")
        builder.setItems(conversationNames) { _, which ->
            val selectedConversation = conversationList[which]
            handleLoadedObjectConversation(selectedConversation)
        }
        builder.show()
    }

    fun loadConversation(isFromNuvem: Boolean) {
        if (isFromNuvem) {
            //carregar do firebase
            val conversationsRef = FirebaseInstance.firebaseDatabase.getReference("conversations/${mainActivity.userId.toString()}")
            conversationsRef.get().addOnSuccessListener { dataSnapshot ->
                if (dataSnapshot.exists()) {
                    val conversation = dataSnapshot.getValue(Conversation::class.java)
                    if (conversation != null) {
                        // Handle loaded conversation object
                    } else {
                        Toast.makeText(context, "Nenhuma conversa encontrada", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Nenhuma conversa encontrada", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val folderName = "conversas_samay_vr"

            // Update the folder path to use internal storage
            val folderPath = File(context.filesDir, folderName)

            // Get a list of all .json files in the folder
            val files = folderPath.listFiles { _, name -> name.endsWith(".json") }

            // Iterate through each file and load the conversation
            files?.forEach { file ->
                var inputStream: FileInputStream? = null
                try {
                    inputStream = FileInputStream(file)
                    val size = inputStream.available()
                    val buffer = ByteArray(size)
                    inputStream.read(buffer)
                    inputStream.close()
                    val jsonString = String(buffer, Charsets.UTF_8)
                    handleLoadedConversation(jsonString)
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    e.printStackTrace()
                } finally {
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun handleLoadedConversation(jsonString: String) {
        try {
            val gson = Gson()
            val conversation = gson.fromJson(jsonString, Conversation::class.java)

            mainActivity.messageList.clear()
            mainActivity.messageList.addAll(conversation.messages)
            mainActivity.msgs.clear()
            mainActivity.msgs.addAll(conversation.messages.map {
                ChatMessage(if (it.isReceived) "assistant" else "user", it.message)
            })
            mainActivity.onConversationDataLoaded()

            Log.i("handleLoadedConversation", "Loaded messages: ${mainActivity.messageList.joinToString { it.toString() }}")
            mainActivity.chatAdapter?.notifyDataSetChanged()
            Toast.makeText(context, "Conversa carregada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            e.printStackTrace()
        }
    }

    private fun handleLoadedObjectConversation(conversation: Conversation) {
        try {
            mainActivity.messageList.clear()
            mainActivity.messageList.addAll(conversation.messages)

            mainActivity.msgs.clear()
            mainActivity.msgs.addAll(conversation.messages.map {
                ChatMessage(if (it.isReceived) "assistant" else "user", it.message)
            })
            Log.i("handleLoadedObjectConversation", "Loaded messages: ${mainActivity.messageList.joinToString { it.toString() }}")
            mainActivity.chatAdapter?.notifyDataSetChanged()
            mainActivity.onConversationDataLoaded()
            Toast.makeText(context, "Conversa carregada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            e.printStackTrace()
        }
    }

    fun saveReportConversation(
        conversation: Conversation
    ) {
        conversation.fileName = "report"
        // data atual formatada BR
        val data = DateTime(System.currentTimeMillis())
        val dataFormatada = data.toString().substring(8, 10) + "/" + data.toString().substring(5, 7) + "/" + data.toString().substring(0, 4)
        conversation.dataCriada = dataFormatada
        conversation.horaCriada = LocalTime.now().toString()

        val firebaseDatabase = FirebaseDatabase.getInstance()
        val reportsRef = firebaseDatabase.getReference("reports/${mainActivity.userId.toString()}")
        val reportRef = reportsRef.push()
        reportRef.setValue(conversation)
            .addOnSuccessListener {
                val reportId = reportRef.key
                Toast.makeText(context, "Report salvo com sucesso!", Toast.LENGTH_SHORT).show()
                Log.d("saveReportConversation", "Report salvo no Firebase com ID: $reportId")
                mainActivity.messageList.clear()
                mainActivity.chatAdapter?.notifyDataSetChanged()
                mainActivity.addInitialMessage()
            }
            .addOnFailureListener {
                Log.d("saveReportConversation", it.toString())
                Toast.makeText(context, "Erro ao salvar no Firebase", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveConversation(
        fileName: String,
        conversation: Conversation,
        saveRemote: Boolean = false,
        exit: Boolean = false
    ) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(conversation)
        conversation.fileName = fileName
        // data atual formatada BR
        val data = DateTime(System.currentTimeMillis())
        val dataFormatada = data.toString().substring(8, 10) + "/" + data.toString().substring(5, 7) + "/" + data.toString().substring(0, 4)
        conversation.dataCriada = dataFormatada
        conversation.horaCriada = LocalTime.now().toString()
        if (saveRemote) {
            val firebaseDatabase = FirebaseInstance.firebaseDatabase
            val conversationsRef = firebaseDatabase.getReference("conversations/${mainActivity.userId.toString()}")
            val conversationRef = conversationsRef.push()
            conversationRef.setValue(conversation)
                .addOnSuccessListener {
                    val conversationId = conversationRef.key
                    Toast.makeText(context, "Conversa salva com sucesso!", Toast.LENGTH_SHORT).show()
                    Log.d("saveConversation", "Conversa salva no Firebase com ID: $conversationId")
                    if (exit) {
                        val intent = Intent(context, SignIn::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        FirebaseAuth.getInstance().signOut()
                        mainActivity.startActivity(intent)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Erro ao salvar no Firebase", Toast.LENGTH_SHORT).show()
                }
        } else {
            val folderName = "conversas_samay_vr"

            // Update the file path to use internal storage
            val folderPath = File(context.filesDir, folderName)
            folderPath.mkdirs()

            val file = File(folderPath, fileName)

            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(file)
                outputStream.write(jsonString.toByteArray())
                Toast.makeText(
                    context.applicationContext,
                    "Conversa Salva em $folderName",
                    Toast.LENGTH_SHORT
                ).show()

                val filePath = file.absolutePath
                Log.i("saveConversation", "File has been saved at: $filePath")
                Toast.makeText(context, "Conversa salva com sucesso!", Toast.LENGTH_SHORT).show()
                if (exit) {
                    val intent = Intent(context, SignIn::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    FirebaseAuth.getInstance().signOut()
                    mainActivity.startActivity(intent)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Toast.makeText(
                    context.applicationContext,
                    "Falha ao salvar conversa",
                    Toast.LENGTH_SHORT
                ).show()
                e.printStackTrace()
            } finally {
                try {
                    outputStream?.close()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteConversationFolder() {
        val conversationsRef = FirebaseInstance.firebaseDatabase.getReference("conversations/${mainActivity.userId.toString()}")
        conversationsRef.removeValue()
            .addOnSuccessListener {
                // Folder deleted successfully
            }
            .addOnFailureListener {
                // Handle failure to delete folder
            }
    }
}
