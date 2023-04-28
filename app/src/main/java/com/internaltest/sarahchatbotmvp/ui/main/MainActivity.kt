package com.internaltest.sarahchatbotmvp.ui.main

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.utils.ShowNotification
import com.internaltest.sarahchatbotmvp.ui.adapters.ChatAdapter
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.models.Message
import com.internaltest.sarahchatbotmvp.ui.wallet.Wallet
import com.theokanning.openai.OpenAiApi
import com.theokanning.openai.completion.chat.ChatCompletionChoice
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.service.OpenAiService
import com.theokanning.openai.service.OpenAiService.buildApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import androidx.appcompat.widget.SwitchCompat

//TODO adicionar menu dropdown
//TODO msgs são apagadas quando o usuário sai da tela. Restaurar a conversa e adicionar opção para
//apagar/reiniciar a conversa
@SuppressLint("NotifyDataSetChanged")
class MainActivity : BaseActivity() {
    private lateinit var firestoreRepo: FirestoreRepo
    private var msgCounter = 0
    private var chatView: RecyclerView? = null
    private var chatAdapter: ChatAdapter? = null
    private var messageList: MutableList<Message> = ArrayList()
    private var editMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var configProfile: FloatingActionButton? = null
    private var progressDialog: ProgressDialog? = null
    private var btnToggleDark: SwitchCompat? = null
    private var btnShop: Button? = null
    private var subscriptionHeadTextView: TextView? = null
    private var creditsHeadTextView: TextView? = null
    private var subscriptionTextView: TextView? = null
    private var creditsTextView: TextView? = null
    private var userId: String? = null
    private var pressedTime: Long = 0
    private lateinit var wallet: Wallet
    private var isDarkModeOn: Boolean? = null
    private var isRewardPopupShown = false
    private var isDailyLoginRewardRunning = false

    private val msgs: MutableList<ChatMessage> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestoreRepo = FirestoreRepo()
        CoroutineScope(Dispatchers.Main).launch {
            isDarkModeOn = firestoreRepo.darkMode.first()
            btnToggleDark?.isChecked = isDarkModeOn as Boolean
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkModeOn as Boolean) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            )
        }
        setContentView(R.layout.activity_main)
        loadFireStoreInfo()
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        chatAdapter = ChatAdapter(messageList, this)
        chatView = findViewById(R.id.chatView)
        with(chatView) { this?.setAdapter(chatAdapter) }
        btnToggleDark = findViewById(R.id.btnToggleDark)
        btnShop = findViewById(R.id.btnShop)
        with(btnShop) { this?.setOnClickListener { gotoShop() } }
        configProfile = findViewById(R.id.config)
        with(configProfile) { this?.setOnClickListener { gotoProfile() } }
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this)!!
        userId = googleSignInAccount.id
        creditsHeadTextView = findViewById(R.id.creditsHead)
        subscriptionHeadTextView = findViewById(R.id.subscriptionHead)
        creditsTextView = findViewById(R.id.creditsCount)
        subscriptionTextView = findViewById(R.id.subscriptionStatus)
        wallet = Wallet()
        wallet.checkAndSwitchEntitlements()
        dailyLoginReward()
        scheduleNotification()

        //implementando lógica do botão modo escuro
        btnToggleDark?.setOnClickListener {
            val isChecked = btnToggleDark?.isChecked ?: false
            btnToggleDark!!.isChecked = !isChecked // Revert the switch state temporarily
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setMessage(
                "Tem certeza? Todo o histórico de conversa será apagado ao trocar tema"
            )
            // Set the positive button with the default text color
            alertDialogBuilder.setPositiveButton("Sim", null)
            // Set the negative button with the default text color
            alertDialogBuilder.setNegativeButton("Não", null)

            val alertDialog = alertDialogBuilder.create()
            alertDialog.setOnShowListener {
                // Get the current theme
                val currentTheme = AppCompatDelegate.getDefaultNightMode()

                // Set the text color for the positive and negative buttons based on the current theme
                val buttonTextColor =
                    if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setTextColor(buttonTextColor)
                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setTextColor(buttonTextColor)

                // Set the click listeners for the buttons after setting the text color
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    // Toggle the dark mode value and update it in Firestore

                    val newDarkModeValue = !(isDarkModeOn ?: false)
                    isDarkModeOn = newDarkModeValue
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.setDarkMode(newDarkModeValue)
                        Log.i("new dark mode value", newDarkModeValue.toString())
                    }
                    alertDialog.dismiss() // Close the dialog after handling the click

                    applyDarkMode(isDarkModeOn) // Apply the new theme
                }

                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                    alertDialog.dismiss() // Close the dialog after handling the click
                    btnToggleDark!!.isChecked = !isChecked // Revert the switch state
                }
            }
            alertDialog.show()
        }

        btnSend?.setOnClickListener {
            val message = editMessage?.text.toString()
            if (message.isNotEmpty()) {
                messageList.add(Message(message, false))
                with(editMessage) { this?.setText("") }
                checkSubscriptionAndCreditsAndStartMessageLoop(message)
                //mensagem de carregamento, que aparecerá qndo o user mandar msg
                progressDialog = ProgressDialog(this@MainActivity)
                progressDialog!!.setTitle("Carregando mensagem")
                progressDialog!!.setMessage("Aguarde...")
                progressDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
                progressDialog!!.show()
                progressDialog!!.setCancelable(false)
                Objects.requireNonNull(with(chatView) { this?.adapter })
                    .notifyDataSetChanged()
                Objects.requireNonNull(with(chatView) { this?.layoutManager })
                    ?.scrollToPosition(messageList.size - 1)
            } else {
                Toast.makeText(this@MainActivity, "Inserir Texto", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        //msgs de aviso e para começar a conversação
        Objects.requireNonNull(with(chatView)
        { this?.layoutManager })?.scrollToPosition(messageList.size - 1)
        messageList.add(
            Message(
                "A seguir, você terá uma conversa com uma amiga virtual, chamada Samay. " +
                        "A assistente é útil, criativa, inteligente e muito amigável. " +
                        "Divirta-se usando a Samay!", true
            )
        )
        chatAdapter!!.notifyDataSetChanged()
        Objects.requireNonNull(with(chatView)
        { this?.layoutManager })?.scrollToPosition(messageList.size - 1)
    }
    private fun applyDarkMode(isDarkModeOn: Boolean?) {
        if (isDarkModeOn == true) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        btnToggleDark!!.isChecked = isDarkModeOn ?: false
    }
    private fun scheduleNotification() {
        val workRequest =
            PeriodicWorkRequest.Builder(ShowNotification::class.java, 1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "notification", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun loadFireStoreInfo() {
        // Observe dark mode changes
        firestoreRepo.darkMode.asLiveData().observe(this) {
            isDarkModeOn = it
            Log.i("is dark mode on (load data store)", isDarkModeOn.toString())
        }

        // Observe credits changes
        firestoreRepo.credits.asLiveData().observe(this) { credits ->
            with(creditsTextView) { this?.text = credits.toString() }
            Log.i("credits (load data store)", credits.toString())
        }

        // Observe subscription status changes
        firestoreRepo.subscriptionStatus.asLiveData().observe(this) { subscription ->
            with(subscriptionTextView) { this?.text = subscription }
            Log.i("subscription (load data store)", subscription)
        }

        lifecycleScope.launch {
            firestoreRepo.fetchData()
        }
    }

    private fun dailyLoginReward() {
        if (isRewardPopupShown || isDailyLoginRewardRunning) return

        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH] + 1
        val day = calendar[Calendar.DAY_OF_MONTH]
        val todaystring = year.toString() + "" + month + "" + day + ""

        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.fetchDailyLoginReward()
            val lastLoginDay = firestoreRepo.dailyLoginDay.first()

            if (lastLoginDay.isEmpty()) {
                // New user, show the reward popup
                Log.i("showrewardpopup", "isempty")
                showRewardPopup()
            } else {
                val lastLoginDate =
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(lastLoginDay)
                val currentDate =
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(todaystring)
                val diffInMillis = currentDate!!.time - lastLoginDate!!.time
                val diffInHours = TimeUnit.MILLISECONDS.toHours(diffInMillis)

                if ((diffInHours >= 24 && isZoneAutomatic(this@MainActivity)
                            && isTimeAutomatic(this@MainActivity)
                            && lastLoginDay != todaystring) && !isRewardPopupShown
                ) {
                    Log.i("showrewardpopup", "lastlogindate")
                    showRewardPopup()
                } else {
                    Toast.makeText(
                        this@MainActivity, "Você já " +
                                "pegou seus créditos grátis", Toast.LENGTH_SHORT
                    ).show()
                }
            }
            isDailyLoginRewardRunning = false
        }
    }

    private fun showRewardPopup() {
        if (isRewardPopupShown) return

        isRewardPopupShown = true

        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH] + 1
        val day = calendar[Calendar.DAY_OF_MONTH]
        val todaystring = year.toString() + "" + month + "" + day + ""
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Créditos grátis")
        builder.setMessage("Aperte aqui para créditos grátis")
        builder.setPositiveButton("Pegar Créditos") { _, _ ->
            Toast.makeText(this, "Créditos grátis adicionados a sua conta!",
                Toast.LENGTH_LONG).show()

            CoroutineScope(Dispatchers.Main).launch {
                firestoreRepo.credits.first().let { currentCredits ->
                    firestoreRepo.setCredits(currentCredits + 10)
                    firestoreRepo.setDailyLoginDay(todaystring) // Update last_login_date
                    val updatedCredits = firestoreRepo.credits.first()
                    with(creditsTextView) { this?.setText(updatedCredits.toString()) }
                    Log.i("credits (reward)", updatedCredits.toString())
                }
            }
        }
        val alertDialog = builder.create()
        alertDialog.show()

        // Get the current theme
        val currentTheme = AppCompatDelegate.getDefaultNightMode()

        // Set the text color for the positive and negative buttons based on the current theme
        val buttonTextColor = if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonTextColor)
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonTextColor)
    }
    private fun isTimeAutomatic(mainActivity: MainActivity): Boolean {
        return Settings.Global.getInt(
            mainActivity.contentResolver,
            Settings.Global.AUTO_TIME,
            0
        ) == 1
    }
    private fun isZoneAutomatic(mainActivity: MainActivity): Boolean {
        return Settings.Global.getInt(
            mainActivity.contentResolver,
            Settings.Global.AUTO_TIME_ZONE,
            0
        ) == 1
    }
    private fun checkSubscriptionAndCreditsAndStartMessageLoop(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (subscriptionTextView?.text == "PREMIUM" || subscriptionTextView?.text == "GPT4") {
                Log.i("start message Premium/GPT4", message)
                gptMessage(message)
            } else {
                firestoreRepo.credits.first().let { currentCredits ->
                    if (currentCredits > 0) {
                        firestoreRepo.setCredits(currentCredits - 1)
                        // send the message
                        Log.i("start message minus", message)
                        gptMessage(message)
                    } else {
                        runOnUiThread {
                            progressDialog!!.dismiss()
                            CoroutineScope(Dispatchers.IO).launch {
                                firestoreRepo.setCredits(0)
                            }
                            showDialogNoCredits()
                        }
                    }
                }
            }
        }
    }

    private fun showDialogNoCredits() {
        val alertDialog = AlertDialog.Builder(this@MainActivity)
            .setTitle("Você está sem créditos")
            .setMessage("Compre mais ou aguarde 24hrs para ter mais")
            .setPositiveButton("Comprar créditos") { _, _ ->
                gotoShop()
            }
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
        // Get the current theme
        val currentTheme = AppCompatDelegate.getDefaultNightMode()

        // Set the text color for the positive and negative buttons based on the current theme
        val buttonTextColor = if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonTextColor)
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonTextColor)
    }
    private fun gptMessage(clientMessage: String){
         val thread = Thread {
            try {
                val token = "OPEN_AI_API_KEY_HERE"
                val timeout = 2600
                val api: OpenAiApi = buildApi(token, Duration.ofSeconds(timeout.toLong()))
                val service = OpenAiService(api)
                Log.i("\nUser Input:",clientMessage)
                val message = ChatMessage("user", clientMessage)
                val model: String = if (subscriptionTextView?.text == "GPT4"){
                    "gpt-4"
                }else{
                    "gpt-3.5-turbo"
                }
                // Add the initial assistant message with the prompt
                val initialPrompt = "Você é uma amiga virtual chamada Samay. " +
                        "Você é útil, criativa, inteligente, curiosa, amorosa, e muito amigável. " +
                        "Você gosta muito de saber mais sobre o humano com quem está conversando " +
                        "e sobre o mundo. Você fala em Português do Brasil. " +
                        "Evite repetir seu nome frequentemente e lembre-se do contexto e das " +
                        "perguntas anteriores na conversa."
                val initialAssistantMessage = ChatMessage("assistant", initialPrompt)
                Log.i("\ninitialAssistantMessage:", initialAssistantMessage.toString())
                if (msgCounter == 0){
                    msgs.add(initialAssistantMessage)
                    val initialChatCompletionRequest = ChatCompletionRequest.builder()
                        .model(model)
                        .messages(msgs)
                        .temperature(.9)
                        .maxTokens(300)
                        .topP(1.0)
                        .frequencyPenalty(.8)
                        .presencePenalty(.8)
                        .user(userId)
                        .build()
                    val initialChoices =
                        service.createChatCompletion(initialChatCompletionRequest).choices.stream()
                        .map { obj: ChatCompletionChoice -> obj.message }.collect(Collectors.toList())
                    println(initialChoices[0].content.toString())
                    Log.i("\nOpenAi initialChatCompletionRequest:",
                        initialChatCompletionRequest.toString())
                    Log.i("\nOpenAi Initial Output:", initialChoices.toString())
                    Log.i("\nOpenAi msgs:", msgs.toString())
                }

                msgs.add(message)

                val chatCompletionRequest = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(msgs)
                    .temperature(.9)
                    .maxTokens(300)
                    .topP(1.0)
                    .frequencyPenalty(.8)
                    .presencePenalty(.8)
                    .user(userId)
                    .build()

                val choices = service.createChatCompletion(chatCompletionRequest).choices.stream()
                    .map { obj: ChatCompletionChoice -> obj.message }.collect(Collectors.toList())
                println(choices[0].content.toString())
                Log.i("\nOpenAi chatCompletionRequest:",
                    chatCompletionRequest.toString())
                Log.i("\nOpenAi Output:", choices.toString())

                if (choices.isNotEmpty()) {
                    runOnUiThread {
                        progressDialog!!.dismiss()
                        messageList.add(Message(choices[0].content.toString().trim(), true))
                        chatAdapter!!.notifyDataSetChanged()
                        Objects.requireNonNull(chatView!!.layoutManager)
                            ?.scrollToPosition(messageList.size - 1)
                        msgCounter += 1
                        Log.i("msgCounter", msgCounter.toString())
                        if (msgCounter >= 10) {
                            askRatings()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread.start()
    }
    private fun gotoProfile() {
        val intent = Intent(this@MainActivity, ProfileActivity::class.java)
        startActivity(intent)
    }
    private fun gotoShop() {
        val intent = Intent(this@MainActivity, Wallet::class.java)
        startActivity(intent)
    }
// lógica de pedir avaliação na app store
    private fun askRatings() {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task: Task<ReviewInfo?> ->
            if (task.isSuccessful) {
                // We can get the ReviewInfo object
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener { }
            } else {
                // There was some problem, continue regardless of the result.
                Toast.makeText(this, "algo deu errado", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //mostrar confirmação antes de sair quando usuário apertar o botão voltar
    override fun onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
            finish()
        } else {
            Toast.makeText(baseContext, "Aperte voltar de novo para sair", Toast.LENGTH_SHORT)
                .show()
        }
        pressedTime = System.currentTimeMillis()
    }
}