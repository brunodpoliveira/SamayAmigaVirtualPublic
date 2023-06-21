package com.internaltest.sarahchatbotmvp.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.internaltest.sarahchatbotmvp.BuildConfig
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.data.BillingManager
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.models.Message
import com.internaltest.sarahchatbotmvp.ui.adapters.ChatAdapter
import com.internaltest.sarahchatbotmvp.ui.wallet.Wallet
import com.internaltest.sarahchatbotmvp.utils.NotificationScheduler
import com.theokanning.openai.OpenAiApi
import com.theokanning.openai.OpenAiHttpException
import com.theokanning.openai.completion.chat.ChatCompletionChoice
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.service.OpenAiService
import com.theokanning.openai.service.OpenAiService.buildApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

//TODO msgs são apagadas quando o usuário sai da tela. Restaurar a conversa e adicionar opção para
//apagar/reiniciar a conversa
@SuppressLint("NotifyDataSetChanged")
class MainActivity : BaseActivity() {
    private lateinit var firestoreRepo: FirestoreRepo
    private lateinit var billingManager: BillingManager
    private lateinit var wallet: Wallet
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var configProfile: FloatingActionButton

    private var progressDialogFragment: ProgressDialogFragment? = null
    private var thisSessionMsgCounter = 0
    private var chatView: RecyclerView? = null
    private var chatAdapter: ChatAdapter? = null
    private var messageList: MutableList<Message> = ArrayList()
    private var editMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var subscriptionHeadTextView: TextView? = null
    private var creditsHeadTextView: TextView? = null
    private var subscriptionTextView: TextView? = null
    private var creditsTextView: TextView? = null
    private var userId: String? = null
    private var pressedTime: Long = 0
    private var isRewardPopupShown = false
    private var isDailyLoginRewardRunning = false
    private var textToSpeechInitialized = false
    private var speak: FloatingActionButton? = null
    private val REQUEST_CODE_SPEECH_INPUT = 1

    private val msgs: MutableList<ChatMessage> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        firestoreRepo = FirestoreRepo()
        loadFireStoreInfo()
        billingManager = FirestoreRepo().getUserId()?.let { BillingManager(this, it) }!!
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        chatAdapter = ChatAdapter(messageList, this)
        chatView = findViewById(R.id.chatView)
        with(chatView) { this?.setAdapter(chatAdapter) }

        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        configProfile = findViewById(R.id.config)
        configProfile.setOnClickListener {
            // Open the drawer
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Show the ProfileFragment inside the navigation drawer
        supportFragmentManager.beginTransaction()
            .replace(R.id.profile_fragment_container, ProfileFragment())
            .commit()
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this)!!
        userId = googleSignInAccount.id
        creditsHeadTextView = findViewById(R.id.creditsHead)
        subscriptionHeadTextView = findViewById(R.id.subscriptionHead)
        creditsTextView = findViewById(R.id.creditsCount)
        subscriptionTextView = findViewById(R.id.subscriptionStatus)
        speak = findViewById(R.id.speak)!!

        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale("pt", "BR")
                textToSpeechInitialized = true
                if (messageList.isNotEmpty()) {
                    messageList.firstOrNull()?.let { speakText(it.message).toString() }
                }
            }
        }
        speak!!.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            )
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voz para texto")

            val activities: List<ResolveInfo> = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            if (activities.isNotEmpty()) {
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_CODE_SPEECH_INPUT
                        )
                    } else {
                        startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
                    }
                } catch (e: Exception) {
                    Toast
                        .makeText(
                            this@MainActivity,
                            " " + e.message,
                            Toast.LENGTH_SHORT
                        )
                        .show()
                    Log.e("Exception", e.toString())
                }
            } else {
                Toast
                    .makeText(
                        this@MainActivity,
                        "Por favor instale um app de reconhecimento de fala",
                        Toast.LENGTH_LONG).show()
            }
        }
        wallet = Wallet()
        wallet.checkAndSwitchEntitlements()
        dailyLoginReward()
        checkForUpdate()
        NotificationScheduler.scheduleDailyNotification(this)
        NotificationScheduler.scheduleInactiveNotification(this)
        NotificationScheduler.scheduleXmasNotification(this)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val newToken = task.result
                Log.d("Firebase FCM Token", "Generated FCM Token: $newToken")
                CoroutineScope(Dispatchers.IO).launch {
                    firestoreRepo.saveFcmToken(newToken)
                }
            } else {
                Log.e("Firebase FCM Token", "Failed to generate FCM Token", task.exception)
            }
        }

        btnSend?.setOnClickListener {
            val message = editMessage?.text.toString()
            if (message.isNotEmpty()) {
                messageList.add(Message(message, false))
                with(editMessage) { this?.setText("") }
                checkSubscriptionAndCreditsAndStartMessageLoop(message)
                //mensagem de carregamento, que aparecerá qndo o user mandar msg
                showProgressDialog("Carregando Mensagem...")
                Objects.requireNonNull(with(chatView) { this?.adapter })
                    .notifyDataSetChanged()
                Objects.requireNonNull(with(chatView) { this?.layoutManager })
                    ?.scrollToPosition(messageList.size - 1)
            } else {
                Toast.makeText(this@MainActivity, "Inserir Texto", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showProgressDialog(message: String) {
        progressDialogFragment = ProgressDialogFragment.newInstance(message)
        progressDialogFragment?.show(supportFragmentManager, "progressDialog")
    }

    private fun dismissProgressDialog() {
        progressDialogFragment?.dismissAllowingStateLoss()
        progressDialogFragment = null
    }


    private fun addAndSpeakInitialMessage() {
        Objects.requireNonNull(with(chatView) { this?.layoutManager })
            ?.scrollToPosition(messageList.size - 1)
        messageList.add(
            Message(
                "A seguir, você terá uma conversa com uma amiga virtual, chamada Samay. " +
                        "A assistente é útil, criativa, inteligente e muito amigável. " +
                        "Divirta-se usando a Samay!",
                true
            )
        )
        chatAdapter!!.notifyDataSetChanged()
        Objects.requireNonNull(with(chatView) { this?.layoutManager })
            ?.scrollToPosition(messageList.size - 1)

    }

    private fun loadFireStoreInfo() {
        // Observe dark mode changes
        firestoreRepo.darkMode.asLiveData().observe(this) { darkMode ->
            if (darkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            if (messageList.isEmpty()) {
                addAndSpeakInitialMessage()
            }
        }

        firestoreRepo.textToSpeech.asLiveData().observe(this) { textToSpeechEnabled ->
            if (textToSpeechEnabled && messageList.isNotEmpty()) {
                // Speak the initial message
                messageList.firstOrNull()?.let { speakText(it.message).toString() }
                messageList.firstOrNull()?.let { Log.i("addAndSpeakInitialMessage", it.message) }
            } else {
                // Stop Text-to-Speech playback when it's disabled
                textToSpeech.stop()
            }
        }

        // Observe credits changes
        firestoreRepo.startListeningForCreditUpdates()
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
        val todaystring = String.format("%04d%02d%02d", year, month, day)

        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.fetchDailyLoginReward()
            val lastLoginDay = firestoreRepo.dailyLoginDay.first()

            var isOldDate = false
            val lastLoginDate = try {
                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(lastLoginDay)
            } catch (e: ParseException) {
                isOldDate = true
                null
            }

            if (lastLoginDay.isEmpty() || isOldDate || lastLoginDate == null) {
                // New user or old date format, show the reward popup
                Log.i("showrewardpopup", "isempty or old date format")
                Log.i("lastLoginDay", lastLoginDay)
                showRewardPopup()
                if (isOldDate) {
                    // Delete the old date
                    firestoreRepo.deleteOldDailyLoginReward()
                }
            } else {
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
        val todaystring = String.format("%04d%02d%02d", year, month, day)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Créditos grátis")
        builder.setMessage("Aperte aqui para créditos grátis")
        builder.setPositiveButton("Pegar Créditos") { _, _ ->
            Toast.makeText(this, "Créditos grátis adicionados a sua conta!",
                Toast.LENGTH_LONG).show()

            CoroutineScope(Dispatchers.Main).launch {
                firestoreRepo.credits.first().let { currentCredits ->
                    firestoreRepo.setCredits(currentCredits + 15)
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

    private fun speakText(text: String) {
        firestoreRepo.textToSpeech.asLiveData().observe(this) { textToSpeechEnabled ->
            if (textToSpeechInitialized && textToSpeechEnabled) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_id")
            }
        }
    }

    private fun incrementMessageSentCounter() {
        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.totalMsgs.first().let { currentMsgs ->
                firestoreRepo.setTotalMsgs(currentMsgs + 1)
            }
        }
    }

    private fun checkForUpdate() {
        val firestore = FirebaseFirestore.getInstance()
        val appVersionRef = firestore.collection("app_version").document("latest")
        appVersionRef.get().addOnSuccessListener { document ->
            if (document != null) {
                Log.d("Firestore", "Fetched document: ${document.id}")

                val latestVersion = document.getString("latest_version")
                if (latestVersion != null) {
                    Log.d("Firestore", "Fetched latest_version: $latestVersion")
                    if (isUpdateAvailable(latestVersion)) {
                        showUpdatePopup()
                    }
                } else {
                    Log.d("Firestore", "latest_version field not found.")
                }
            } else {
                Log.d("Firestore", "Document not found.")
            }
        }.addOnFailureListener { exception ->
            Log.e("Firestore", "Error fetching document: ", exception)
        }
    }

    private fun isUpdateAvailable(latestVersion: String): Boolean {
        val currentVersion: String
        try {
            currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
            Log.i("currentVersion",currentVersion)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return false
        }
        return currentVersion != latestVersion
    }

    private fun showUpdatePopup() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Atualização Disponível")
        builder.setMessage("Uma nova versão do app está disponível." +
                "Favor atualize para a última versão.")
        builder.setPositiveButton("Atualizar") { _, _ ->
            // Redirect the user to the app's page in the Google Play Store
            val appPackageName = packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
        }
        builder.setNegativeButton("Agora Não", null)
        val alertDialog = builder.create()
        alertDialog.show()

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

    private fun checkSubscriptionAndCreditsAndStartMessageLoop(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (subscriptionTextView?.text == "PREMIUM" || subscriptionTextView?.text == "GPT4") {
                Log.i("start message Premium/GPT4", message)
                gptMessage(message)
                firestoreRepo.onUserActivity(applicationContext)
            } else {
                firestoreRepo.credits.first().let { currentCredits ->
                    if (currentCredits > 0) {
                        firestoreRepo.setCredits(currentCredits - 1)
                        // send the message
                        Log.i("start message minus", message)
                        gptMessage(message)
                        firestoreRepo.onUserActivity(applicationContext)
                    } else {
                        runOnUiThread {
                            dismissProgressDialog()
                            CoroutineScope(Dispatchers.IO).launch {
                                firestoreRepo.setCredits(0)
                                firestoreRepo.onUserActivity(applicationContext)
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

    private fun feedbackPopup() {
        val alertDialog = AlertDialog.Builder(this@MainActivity)
            .setTitle("Responder Formulário?")
            .setMessage("Ganhe 50 créditos grátis respondendo esse formulário")
            .setPositiveButton("Responder formulário") { _, _ ->
                gotoForm()
            }
            .setNegativeButton("Agora não") { dialog, _ ->
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

    private fun handleTimeoutError(elapsedTime: Long, timeout: Int): Boolean {
        if (elapsedTime > timeout * 1000) {
            runOnUiThread {
                dismissProgressDialog()
                CoroutineScope(Dispatchers.Main).launch {
                    firestoreRepo.credits.first().let { currentCredits ->
                        firestoreRepo.setCredits(currentCredits + 1)
                        // Show timeout error to the client
                        Toast.makeText(this@MainActivity, "Erro: " +
                                "O servidor demorou demais para responder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return true
        }
        return false
    }

    private fun gptMessage(clientMessage: String) {
        val thread = Thread {
            try {
                val token = BuildConfig.API_KEY
                val timeout = 30  // Set timeout value to 30 seconds
                val api: OpenAiApi = buildApi(token, Duration.ofSeconds(timeout.toLong()))
                val service = OpenAiService(api)
                Log.i("\nUser Input:", clientMessage)
                val message = ChatMessage("user", clientMessage)
                val model: String = if (subscriptionTextView?.text == "GPT4") {
                    "gpt-4"
                } else {
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

                if (thisSessionMsgCounter == 0) {
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
                    Log.i("\ninitialChatCompletionRequest:", initialChatCompletionRequest.toString())
                    try {
                        val startTime = System.currentTimeMillis()
                        val initialHttpResponse = service.createChatCompletion(initialChatCompletionRequest)
                        val elapsedTime = System.currentTimeMillis() - startTime
                        Log.i("\ninitialHttpResponse:", initialHttpResponse.toString())

                        if (handleTimeoutError(elapsedTime, timeout)) {
                            return@Thread
                        }

                        val initialChoices = initialHttpResponse.choices.stream()
                            .map { obj: ChatCompletionChoice -> obj.message }.collect(Collectors.toList())
                        thisSessionMsgCounter += 1

                        println(initialChoices[0].content.toString())
                        Log.i("\nOpenAi initialChatCompletionRequest:",
                            initialChatCompletionRequest.toString())
                        Log.i("\nOpenAi Initial Output:", initialChoices.toString())
                        Log.i("\nOpenAi msgs:", msgs.toString())
                    } catch (e: OpenAiHttpException) {
                        e.printStackTrace()
                        runOnUiThread {
                            dismissProgressDialog()
                            CoroutineScope(Dispatchers.Main).launch {
                                firestoreRepo.credits.first().let { currentCredits ->
                                    firestoreRepo.setCredits(currentCredits + 1)
                                }
                            }
                            // Show error message to the client
                            Toast.makeText(this@MainActivity,
                                "Erro de Servidor: ${e.message}. Favor tente mais tarde.",
                                Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }
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

                try {
                    val startTimeForChoices = System.currentTimeMillis()
                    val httpResponse = service.createChatCompletion(chatCompletionRequest)
                    val elapsedTimeForChoices = System.currentTimeMillis() - startTimeForChoices

                    if (handleTimeoutError(elapsedTimeForChoices, timeout)) {
                        return@Thread
                    }

                    val choices = httpResponse.choices.stream()
                        .map { obj: ChatCompletionChoice -> obj.message }.collect(Collectors.toList())

                    println(choices[0].content.toString())
                    Log.i("\nOpenAi chatCompletionRequest:",
                        chatCompletionRequest.toString())
                    Log.i("\nOpenAi Output:", choices.toString())

                    if (choices.isNotEmpty()) {
                        runOnUiThread {
                            dismissProgressDialog()
                            messageList.add(Message(choices[0].content.toString().trim(), true))
                            speakText(choices[0].content.toString().trim())
                            Log.i("speakText", messageList[0].toString())
                            chatAdapter!!.notifyDataSetChanged()
                            Objects.requireNonNull(chatView!!.layoutManager)?.scrollToPosition(messageList.size - 1)
                            thisSessionMsgCounter += 1
                            incrementMessageSentCounter()
                            Log.i("thisSessionMsgCounter", thisSessionMsgCounter.toString())
                            if (thisSessionMsgCounter >= 10) {
                                askRatings()
                            }
                            CoroutineScope(Dispatchers.Main).launch {
                                firestoreRepo.totalMsgs.first().let { currentMsgs ->
                                    if (currentMsgs == 20 || currentMsgs == 50){
                                        feedbackPopup()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: OpenAiHttpException) {
                    e.printStackTrace()
                    runOnUiThread {
                        dismissProgressDialog()
                        CoroutineScope(Dispatchers.Main).launch {
                            firestoreRepo.credits.first().let { currentCredits ->
                                firestoreRepo.setCredits(currentCredits + 1)
                                // Show timeout error to the client
                                Toast.makeText(this@MainActivity, "Erro: " +
                                        "O servidor demorou demais para responder",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    return@Thread
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    dismissProgressDialog()
                    // Show error message to the client
                    Toast.makeText(this@MainActivity,
                        "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        thread.start()
    }

    private fun gotoShop() {
        val intent = Intent(this@MainActivity, Wallet::class.java)
        startActivity(intent)
    }

    private fun gotoForm() {
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://forms.gle/qZ58bYfMQMew7S6P6")
        )
        startActivity(browserIntent)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                val res: ArrayList<String> =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>
                editMessage?.setText(Objects.requireNonNull(res)[0])
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the listener when the user logs out or the activity is destroyed
        firestoreRepo.userDocListenerRegistration.remove()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}