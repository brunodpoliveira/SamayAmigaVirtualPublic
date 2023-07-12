package com.internaltest.sarahchatbotmvp.ui.wallet

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.asLiveData
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QEntitlementRenewState
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class Wallet : BaseActivity(){
    private lateinit var firestoreRepo: FirestoreRepo
    private val credits: Flow<Int> by lazy { firestoreRepo.credits }
    private val subscription: Flow<String> by lazy { firestoreRepo.subscriptionStatus }
    private var subscriptionHeadTextView: TextView? = null
    private var creditsHeadTextView: TextView? = null
    var subscriptionTextView: TextView? = null
    var creditsTextView: TextView? = null
    private var btnReturn: TextView? = null
    private var checkAndCancelOfferingButton: TextView? = null

    private val packageNameLink = "com.internaltest.sarahchatbotmvp"
    var offerings by mutableStateOf<List<QOffering>>(emptyList())
        private set
    var hasPremiumEntitlement by mutableStateOf(false)
        private set
    var hasLiteEntitlement by mutableStateOf(false)
        private set
    var hasGPT4Entitlement by mutableStateOf(false)
        private set

    init {
        firestoreRepo = FirestoreRepo()
        loadOfferings()
        loadDataStoreInfo()
        checkAndSwitchEntitlements()
    }

    private fun loadDataStoreInfo() {
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        firestoreRepo = FirestoreRepo()
        creditsHeadTextView = findViewById(R.id.creditsHead)
        subscriptionHeadTextView = findViewById(R.id.subscriptionHead)
        btnReturn = findViewById(R.id.goToProfileActivity)
        checkAndCancelOfferingButton = findViewById(R.id.itemCancel)
        with(btnReturn) { this?.setOnClickListener { gotoMainActivity() } }
        with(checkAndCancelOfferingButton) { this?.setOnClickListener { openPlaystoreAccount() } }
        uiOfferings()
        if(intent.getBooleanExtra("SHOW_BUY_CREDITS", false)) {
            purchaseBuyCreditsOfferings()
        }
    }

    private fun gotoMainActivity() {
        val intent = Intent(this@Wallet, MainActivity::class.java)
        startActivity(intent)
    }

    private fun openPlaystoreAccount() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/account/subscriptions?&package=$packageNameLink")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                applicationContext,
                "Erro durante processo",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }

    private fun loadOfferings() {
        Qonversion.shared.offerings(
            object : QonversionOfferingsCallback {
                override fun onError(error: QonversionError) {
                    // Handle error
                    Log.d(ContentValues.TAG, "onError: ${error.description}")
                }
                override fun onSuccess(offerings: QOfferings) {
                    this@Wallet.offerings = offerings.availableOfferings
                }
            }
        )
    }

    override fun onBackPressed() {
        Toast.makeText(applicationContext,
            "Botão voltar desligado. Use as opções do menu", Toast.LENGTH_LONG).show()
    }

    private fun updateGPT4Entitlement() {
        Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                hasGPT4Entitlement = entitlements["gpt4_Entitlement"]?.isActive == true
                Log.d(ContentValues.TAG, "Entitlements: ${entitlements.keys.toList()}")
                Log.i("subscription (update gpt4)", entitlements.toString())
                checkAndSwitchEntitlements()
            }
        })
    }

    private fun updatePremiumEntitlement() {
        Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                hasPremiumEntitlement = entitlements["premium_Entitlement"]?.isActive == true
                Log.d(ContentValues.TAG, "Entitlements: ${entitlements.keys.toList()}")
                Log.i("subscription (update premium)", entitlements.toString())
                checkAndSwitchEntitlements()
            }
        })
    }

    private fun updateLiteEntitlement() {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                hasLiteEntitlement = true
                Log.d(ContentValues.TAG, "Entitlements: ${entitlements.keys.toList()}")
                Log.i("subscription (update lite)", entitlements.toString())
                checkAndSwitchEntitlements()
            }
        })
    }

    fun addCredits(newCredits: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.credits.first().let { currentCredits ->
                firestoreRepo.setCredits(currentCredits + newCredits)
            }
        }
        checkAndSwitchEntitlements()
    }

    private suspend fun setSubscriptionStatus(status: String) {
        firestoreRepo.setSubscriptionStatus(status)
        firestoreRepo.subscriptionStatus.collect { subscription ->
            with (subscriptionTextView) { this?.text = subscription }
            Log.i("subscription (checkEntitlements $status)", subscription)
        }
    }

    //TODO corrigir bug (parece bug do qonverson) onde ele só atualiza o QProductRenewState quando
    //o usuário sai e volta pro app
    fun checkAndSwitchEntitlements() {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                if (entitlements["gpt4_entitlement"]?.isActive == true) {
                    hasGPT4Entitlement = true
                    CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("GPT4") }
                    handleRenewState(entitlements["gpt4_entitlement"])
                } else if (entitlements["premium_entitlement"]?.isActive == true) {
                    hasPremiumEntitlement = true
                    CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("PREMIUM") }
                    handleRenewState(entitlements["premium_entitlement"])
                } else if(entitlements["lite_entitlement"]?.isActive == true) {
                    hasLiteEntitlement = true
                    CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("LITE") }
                    handleRenewState(entitlements["lite_entitlement"])
                } else {
                    CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("NENHUMA") }
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

    private fun handleRenewState(entitlement: QEntitlement?) {
        // Handle the renew state if needed
        when (entitlement?.renewState) {
            QEntitlementRenewState.WillRenew -> {
                // Subscription will renew
                Log.i("renew state", "will renew")
            }
            QEntitlementRenewState.NonRenewable -> {
                // Subscription won't renew
                Toast.makeText(
                    applicationContext,
                    "Produto Não Renovável",
                    Toast.LENGTH_LONG
                ).show()
            }
            QEntitlementRenewState.BillingIssue -> {
                // Issue with billing
            }
            QEntitlementRenewState.Canceled ->{
                Toast.makeText(
                    applicationContext,
                    "Assinatura cancelada, mas ainda ativa até o fim do ciclo de " +
                            "faturamento atual ", Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                // Do nothing, subscription has expired
            }
        }
    }

    private fun uiOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(offerings: QOfferings) {
                val mainOffering = offerings.main
                val gpt4Offering = offerings.offeringForID("gpt4_offering")
                val premiumOffering = offerings.offeringForID("premium_offering")
                val liteOffering = offerings.offeringForID("lite_offering")
                val buyCreditsOffering = offerings.offeringForID("buy_credits_offering")

                if (mainOffering != null && mainOffering.products.isNotEmpty()) {
                    Log.i("test offerings", mainOffering.products.toString())
                    subscriptionTextView = findViewById(R.id.subscriptionStatus)
                    subscriptionTextView?.text = subscription.toString()
                    creditsTextView = findViewById(R.id.creditsCount)
                    creditsTextView?.text = credits.toString()
                    Log.i("credits text view", creditsTextView.toString())

                    val gpt4OfferingTextView = findViewById<TextView>(R.id.itemNameGPT4Offering)
                    val premiumOfferingTextView = findViewById<TextView>(R.id.itemNamePremiumOffering)
                    val liteOfferingTextView = findViewById<TextView>(R.id.itemNameLiteOffering)
                    val buyCreditsTextView = findViewById<TextView>(R.id.itemName)

                    val gpt4OfferingButton = findViewById<Button>(R.id.itemPriceGPT4Offering)
                    val premiumOfferingButton = findViewById<Button>(R.id.itemPricePremiumOffering)
                    val liteOfferingButton = findViewById<Button>(R.id.itemPriceLiteOffering)
                    val buyCreditsButton = findViewById<Button>(R.id.itemPrice)

                    val gpt4OfferingDescriptionTextView =
                        findViewById<TextView>(R.id.itemDescriptionGPT4Offering)
                    val premiumOfferingDescriptionTextView =
                        findViewById<TextView>(R.id.itemDescriptionPremiumOffering)
                    val liteOfferingButtonDescriptionTextView =
                        findViewById<TextView>(R.id.itemDescriptionLiteOffering)
                    val buyCreditsDescriptionTextView = findViewById<TextView>(R.id.itemDescription)

                    val gpt4OfferingInfo = gpt4Offering?.products?.get(0)
                    val premiumOfferingInfo = premiumOffering?.products?.get(0)
                    val liteOfferingInfo = liteOffering?.products?.get(0)
                    val buyCreditsInfo = buyCreditsOffering?.products?.get(0)

                    gpt4OfferingTextView.text = gpt4OfferingInfo?.skuDetail!!.title
                    premiumOfferingTextView.text = premiumOfferingInfo?.skuDetail!!.title
                    liteOfferingTextView.text = liteOfferingInfo?.skuDetail!!.title
                    buyCreditsTextView.text = buyCreditsInfo?.skuDetail!!.title

                    gpt4OfferingButton.text = gpt4OfferingInfo.skuDetail!!.price
                    premiumOfferingButton.text = premiumOfferingInfo.skuDetail!!.price
                    liteOfferingButton.text = liteOfferingInfo.skuDetail!!.price
                    buyCreditsButton.text = buyCreditsInfo.skuDetail!!.price

                    gpt4OfferingDescriptionTextView.text = gpt4OfferingInfo.skuDetail!!.description
                    premiumOfferingDescriptionTextView.text = premiumOfferingInfo.skuDetail!!.description
                    liteOfferingButtonDescriptionTextView.text = liteOfferingInfo.skuDetail!!.description
                    buyCreditsDescriptionTextView.text = buyCreditsInfo.skuDetail!!.description

                    gpt4OfferingButton.setOnClickListener {
                        purchaseOffering(gpt4Offering.products[0])
                    }
                    premiumOfferingButton.setOnClickListener {
                        purchaseOffering(premiumOffering.products[0])
                    }
                    liteOfferingButton.setOnClickListener {
                        purchaseOffering(liteOffering.products[0])
                    }
                    buyCreditsButton.setOnClickListener {
                        purchaseOffering(buyCreditsOffering.products[0])
                    }
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

    private fun purchaseOffering(qProduct: QProduct) {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                if (hasActiveSubscription(entitlements)) {
                    Toast.makeText(applicationContext,
                        "Você tem uma assinatura ativa. " +
                                "Cancele-a primeiro antes de fazer outra assinatura.",
                        Toast.LENGTH_LONG).show()
                } else{
                    Qonversion.shared.purchase(this@Wallet, qProduct,
                        callback = object: QonversionEntitlementsCallback {
                        override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                            Log.i("qproduct", qProduct.toString())

                            if (qProduct.storeID == "assinatura_gpt4"
                                || qProduct.qonversionID == "assinatura_gpt4") {
                                updateGPT4Entitlement()
                            }

                            if (qProduct.storeID == "assinatura_premium"
                                || qProduct.qonversionID == "assinatura_premium") {
                                updatePremiumEntitlement()
                            }

                            if (qProduct.storeID == "assinatura_lite"
                                || qProduct.qonversionID == "assinatura_lite") {
                                updateLiteEntitlement()
                            }

                            if (qProduct.storeID == "buy_credits"
                                || qProduct.qonversionID == "buy_credits")
                            {
                                addCredits(75)
                            }
                        }
                        override fun onError(error: QonversionError) {
                            Log.d(ContentValues.TAG, "onError: ${error.description}")
                        }
                    })
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

    private fun hasActiveSubscription(entitlements: Map<String, QEntitlement>): Boolean {
        return entitlements.any {
            ((
                    (it.value.productId == "assinatura_gpt4") ||
                            (it.value.productId == "assinatura_premium") ||
                            (it.value.productId == "assinatura_lite")
                    )
                    && it.value.isActive)
        }
    }

    private fun purchaseBuyCreditsOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) {
                val buyCreditsOffering = offerings.offeringForID("buy_credits_offering")
                buyCreditsOffering?.let {
                    purchaseOffering(it.products[0])
                }
            }

            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }
}
