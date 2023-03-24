package com.internaltest.sarahchatbotmvp

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import com.internaltest.sarahchatbotmvp.adapters.ChatAdapter
import com.internaltest.sarahchatbotmvp.data.DataStoreRepo
import com.internaltest.sarahchatbotmvp.login.ProfileActivity
import com.internaltest.sarahchatbotmvp.models.Message
import com.internaltest.sarahchatbotmvp.wallet.Wallet
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QEntitlementRenewState
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.theokanning.openai.OpenAiApi
import com.theokanning.openai.completion.chat.ChatCompletionChoice
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.service.OpenAiService
import com.theokanning.openai.service.OpenAiService.buildApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.*
import java.util.stream.Collectors

//TODO adicionar menu dropdown
//TODO msgs são apagadas quando o usuário sai da tela. Restaurar a conversa e adicionar opção para
//apagar/reiniciar a conversa
class MainActivity : AppCompatActivity() {
    lateinit var dataStoreRepo: DataStoreRepo
    private var isDarkModeOn: Boolean? = null
    private val credits: Flow<Int> by lazy { dataStoreRepo.getCredits }
    private var msgCounter = 0
    private var chatView: RecyclerView? = null
    private var chatAdapter: ChatAdapter? = null
    private var messageList: MutableList<Message> = ArrayList()
    private var editMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var configProfile: FloatingActionButton? = null
    private var progressDialog: ProgressDialog? = null
    private var btnToggleDark: Button? = null
    private var btnDailyReward: Button? = null
    private var btnShop: Button? = null
    private var subscriptionHeadTextView: TextView? = null
    private var creditsHeadTextView: TextView? = null
    var subscriptionTextView: TextView? = null
    private var creditsTextView: TextView? = null
    private var userId: String? = null
    private var pressedTime: Long = 0
    var hasPremiumPermission by mutableStateOf(false)
        private set
    var hasLitePermission by mutableStateOf(false)
        private set
    private val msgs: MutableList<ChatMessage> = ArrayList()
    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dataStoreRepo = DataStoreRepo(this)
        loadDataStoreInfo()
        //TODO usar o checkandswitch em wallet.tk
        chatView = findViewById(R.id.chatView)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        chatAdapter = ChatAdapter(messageList, this)
        with(chatView) { this?.setAdapter(chatAdapter) }
        btnToggleDark = findViewById(R.id.btnToggleDark)
        btnDailyReward = findViewById(R.id.btnDailyReward)
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
        dailyLoginReward()
        checkAndSwitchPermissions()

        // ativado quando o user reabre o app dps de aplicar o modo claro/escuro
        CoroutineScope(Dispatchers.Main).launch {
            isDarkModeOn = dataStoreRepo.getDarkMode.first()
            with(btnToggleDark) {
                this?.text = if (isDarkModeOn as Boolean) {
                    "Desligar Modo Escuro"
                } else {
                    "Ligar Modo Escuro"
                }
            }
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkModeOn as Boolean) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            )
        }

        //implementando lógica do botão modo escuro
        btnToggleDark?.setOnClickListener {
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setMessage("Tem certeza? Todo o histórico de coversa será apagado " +
                    "ao trocar tema")
            alertDialog.setPositiveButton(Html.fromHtml("<font color=\"#333333\">Sim</font>"))
            { _, _ ->
                GlobalScope.launch {
                    isDarkModeOn = !isDarkModeOn!!
                    dataStoreRepo.setDarkMode(isDarkModeOn!!)
                }
                with(btnToggleDark) {
                    this?.text = if (isDarkModeOn == true) {
                        "Desligar Modo Escuro"
                    } else {
                        "Ligar Modo Escuro"
                    }
                }
                AppCompatDelegate.setDefaultNightMode(
                    if (isDarkModeOn == true) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_NO
                    }
                )
            }
            alertDialog.setNegativeButton(Html.fromHtml("<font color=\"#333333\">Não</font>"))
            { dialog, _ -> dialog.dismiss() }
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
                Objects.requireNonNull(with(chatView) { this?.adapter }).notifyDataSetChanged()
                Objects.requireNonNull(with(chatView) { this?.layoutManager })
                    ?.scrollToPosition(messageList.size - 1)
            } else {
                Toast.makeText(this@MainActivity, "Inserir Texto", Toast.LENGTH_SHORT).show()
            }
        }
        // This method will be executed once the timer is over
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if(msgCounter == 0){
                    gpt3TurboInitMessage()
                }
            },
            2000 // value in milliseconds
        )

    }
    private fun loadDataStoreInfo() {
         this.dataStoreRepo.getDarkMode.asLiveData().observe(this) {
            isDarkModeOn = it
             Log.i("is dark mode on (load data store)", isDarkModeOn.toString())

         }

        CoroutineScope(Dispatchers.Main).launch {
            dataStoreRepo.getCredits.collect { credits ->
                with(creditsTextView) { this?.setText(credits.toString()) }
                Log.i("credits (load data store)", credits.toString())

            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            dataStoreRepo.getSubscriptionStatus.collect { subscription ->
                with(subscriptionTextView) { this?.setText(subscription) }
                Log.i("subscription (load data store)", subscription)
            }
        }
    }
    @SuppressLint("SetTextI18n")
    private fun dailyLoginReward() {
        //TODO trocar para datastore e ver ser vale a pena
        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH]
        val day = calendar[Calendar.DAY_OF_MONTH]
        val todaystring = year.toString() + "" + month + "" + day + ""
        val timepref = getSharedPreferences("REWARD", 0)
        val currentday = timepref.getBoolean(todaystring, false)

        //Daily reward
        if (!currentday && isZoneAutomatic(this) && isTimeAutomatic(this)) {
            //currentday =false
            btnDailyReward!!.isEnabled = true
            btnDailyReward!!.text = "Aperte aqui para créditos grátis"
            btnDailyReward!!.setOnClickListener {
                Toast.makeText(
                    this,
                    "Créditos grátis adicionada a sua conta!",
                    Toast.LENGTH_LONG
                ).show()
                // Do your stuff here
                CoroutineScope(Dispatchers.Main).launch {
                    dataStoreRepo.getCredits.first().let { currentCredits ->
                        dataStoreRepo.setCredits(currentCredits + 10)
                        dataStoreRepo.getCredits.collect { credits ->
                                with(creditsTextView) { this?.setText(credits.toString()) }
                                Log.i("credits (reward)", credits.toString())

                            }
                        }
                    }
                // saving the date
                val timedaily = timepref.edit()
                timedaily.putBoolean(todaystring, true)
                timedaily.apply()
                btnDailyReward!!.text = "Espere 24 hrs"
                btnDailyReward!!.isEnabled = false
                creditsTextView!!.text = credits.toString()
            }
        } else {
            btnDailyReward!!.text = "Espere 24 hrs"
            btnDailyReward!!.isEnabled = false
            Toast.makeText(this, "Você já pegou seus créditos grátis", Toast.LENGTH_SHORT).show()
        }
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
    //TODO usar o checkandswitch em wallet.tk
    private fun checkAndSwitchPermissions() {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                val premiumPermission = entitlements["premium_permission"]
                val litePermission = entitlements["lite_permission"]

                if (premiumPermission != null && premiumPermission.isActive && hasPremiumPermission) {
                    // handle active permission here
                    CoroutineScope(Dispatchers.Main).launch {
                        dataStoreRepo.getSubscriptionStatus.first().let {
                            dataStoreRepo.setSubscriptionStatus("PREMIUM")
                            hasPremiumPermission = entitlements["premium_permission"]?.isActive == true
                            dataStoreRepo.getSubscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkPermissions premium)", subscription)
                            }
                        }
                    }
                    // also you can check renew state if needed
                    // for example to check if user has canceled subscription and offer him a discount
                    when (premiumPermission.renewState) {
                        QEntitlementRenewState.WillRenew ->
                            Log.i("renew state will renew", "will renew")
                        // WillRenew is the state of an auto-renewable subscription
                        // NonRenewable is the state of consumable/non-consumable IAPs that could unlock lifetime access
                        QEntitlementRenewState.BillingIssue ->
                            // Prompt the user to update the payment method.
                            // TODO adicionar popup de aviso no topo da tela se condição == true
                            Toast.makeText(
                                applicationContext,
                                "Problema detectado com método de pagamento. " +
                                        "Clique no botão Compre mais créditos para mais informações",
                                Toast.LENGTH_LONG
                            ).show()
                        QEntitlementRenewState.Canceled ->
                            // The user has turned off auto-renewal for the subscription, but the subscription has not expired yet.
                            // Prompt the user to resubscribe with a special offer.
                            Toast.makeText(
                                applicationContext,
                                "Auto-renovação da assinatura desativada, mas assinatura ainda válida",
                                Toast.LENGTH_LONG
                            ).show()
                        else -> {
                            // do nothing, subscription is active
                        }
                    }
                }

                if (litePermission != null && litePermission.isActive && hasLitePermission) {
                    // handle active permission here
                    CoroutineScope(Dispatchers.Main).launch {
                        dataStoreRepo.getSubscriptionStatus.first().let {
                            dataStoreRepo.setSubscriptionStatus("LITE")
                            hasLitePermission = entitlements["lite_permission"]?.isActive == true
                            dataStoreRepo.getSubscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkPermissions lite)", subscription)
                            }
                            dataStoreRepo.scheduleMonthlyCredits(300)
                        }
                    }
                    // also you can check renew state if needed
                    // for example to check if user has canceled subscription and offer him a discount
                    when (litePermission.renewState) {
                        QEntitlementRenewState.WillRenew ->
                            Log.i("renew state will renew", "will renew")
                        // WillRenew is the state of an auto-renewable subscription
                        // NonRenewable is the state of consumable/non-consumable IAPs that could unlock lifetime access
                        QEntitlementRenewState.BillingIssue ->
                            // Prompt the user to update the payment method.
                            //TODO adicionar popup de aviso no topo da tela se condição == true
                            Toast.makeText(
                                applicationContext,
                                "Problema detectado com método de pagamento. " +
                                        "Clique no botão Compre mais créditos para mais informações",
                                Toast.LENGTH_LONG
                            ).show()
                        QEntitlementRenewState.Canceled ->
                            // The user has turned off auto-renewal for the subscription, but the subscription has not expired yet.
                            // Prompt the user to resubscribe with a special offer.
                            Toast.makeText(
                                applicationContext,
                                "Auto-renovação da assinatura desativada, mas assinatura ainda válida",
                                Toast.LENGTH_LONG
                            ).show()
                        else -> {
                            // do nothing, subscription is active
                        }
                    }
                }

                if (premiumPermission?.isActive == false && litePermission?.isActive == false) {
                    // subscription is not active
                    CoroutineScope(Dispatchers.Main).launch {
                        dataStoreRepo.getSubscriptionStatus.first().let {
                            dataStoreRepo.setSubscriptionStatus("NENHUMA")
                            hasPremiumPermission = entitlements["premium_permission"]?.isActive == false
                            hasLitePermission = entitlements["lite_permission"]?.isActive == false
                            dataStoreRepo.getSubscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkPermissions not found/not active)", subscription)
                            }
                        }
                    }
                }

                if (litePermission == null || premiumPermission == null)
                {
                    //fallback option in case there's an error
                    CoroutineScope(Dispatchers.Main).launch {
                        dataStoreRepo.getSubscriptionStatus.first().let {
                            dataStoreRepo.setSubscriptionStatus("NENHUMA")
                            hasPremiumPermission = entitlements["premium_permission"]?.isActive == false
                            hasLitePermission = entitlements["lite_permission"]?.isActive == false
                            dataStoreRepo.getSubscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkPermissions not found/not active)", subscription)
                            }
                        }
                    }
                }
            }

            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }
    private fun checkSubscriptionAndCreditsAndStartMessageLoop(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (subscriptionTextView?.text == "PREMIUM") {
                Log.i("start message Premium", message)
                gpt3TurboMessage(message)
            } else {
                dataStoreRepo.getCredits.first().let { currentCredits ->
                    if (currentCredits > 0) {
                        dataStoreRepo.setCredits(currentCredits - 1)
                        // send the message
                        Log.i("start message minus", message)
                        gpt3TurboMessage(message)
                    } else {
                        runOnUiThread {
                            progressDialog!!.dismiss()
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStoreRepo.setCredits(0)
                            }
                            Toast.makeText(this@MainActivity,
                                "Você está sem créditos. Compre-os ou aguarde 24hrs para ter mais",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    private fun gpt3TurboInitMessage(){
        val initPrompt = """ 
            Você é uma amiga virtual chamaga Samay. 
            Você é útil, criativa, inteligente, curiosa e muito amigável. 
            Você gosta muito de saber mais sobre o humano com quem está conversando e sobre o mundo.
            Você fala em Português do Brasil. 
            Você agora vai conversar com um humano. 
            Faça uma rápida introdução de si mesma."""

        progressDialog = ProgressDialog(this@MainActivity)
        progressDialog!!.setTitle("Carregando mensagem inicial")
        progressDialog!!.setMessage("Aguarde...")
        progressDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog!!.show()
        progressDialog!!.setCancelable(true)
        Objects.requireNonNull(with(chatView) { this?.adapter }).notifyDataSetChanged()
        Objects.requireNonNull(with(chatView) { this?.layoutManager })
            ?.scrollToPosition(messageList.size - 1)


        @SuppressLint("NotifyDataSetChanged") val thread = Thread {
            try {
                val token = "OPEN_AI_API_KEY_HERE"
                val timeout = 2600
                val api: OpenAiApi = buildApi(token, Duration.ofSeconds(timeout.toLong()))
                val service = OpenAiService(api)
                Log.i("\nInit Input:",initPrompt)
                val role = "assistant"
                val message = ChatMessage(role, initPrompt)
                msgs.add(message)

                val chatCompletionRequest = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(msgs)
                    .temperature(.9)
                    .maxTokens(150)
                    .topP(1.0)
                    .frequencyPenalty(.6)
                    .presencePenalty(.6)
                    .build()

                val choices = service.createChatCompletion(chatCompletionRequest).choices.stream()
                    .map { obj: ChatCompletionChoice -> obj.message }.collect(Collectors.toList())
                println(choices[0].content.toString())
                Log.i("\nOpenAi gpt-3.5 Output:", choices.toString())

                if (choices.isNotEmpty()) {
                    runOnUiThread {
                        progressDialog!!.dismiss()
                        messageList.add(Message(choices[0].content.toString().trim(), true))
                        chatAdapter!!.notifyDataSetChanged()
                        Objects.requireNonNull(chatView!!.layoutManager)
                            ?.scrollToPosition(messageList.size - 1)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread.start()
    }
    private fun gpt3TurboMessage(clientMessage: String){

        @SuppressLint("NotifyDataSetChanged") val thread = Thread {
            try {
                val token = "OPEN_AI_API_KEY_HERE"
                val timeout = 2600
                val api: OpenAiApi = buildApi(token, Duration.ofSeconds(timeout.toLong()))
                val service = OpenAiService(api)
                Log.i("\nUser Input:",clientMessage)
                val role = "user"
                val message = ChatMessage(role, clientMessage)
                msgs.add(message)

                val chatCompletionRequest = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(msgs)
                    .temperature(.9)
                    .maxTokens(150)
                    .topP(1.0)
                    .frequencyPenalty(.6)
                    .presencePenalty(.6)
                    .user(userId)
                    .build()

                val choices = service.createChatCompletion(chatCompletionRequest).choices.stream()
                    .map { obj: ChatCompletionChoice -> obj.message }.collect(Collectors.toList())
                println(choices[0].content.toString())
                Log.i("\nOpenAi gpt-3.5 Output:", choices.toString())

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
    //TODO usar a mesma lógica do trocar para modo escuro
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