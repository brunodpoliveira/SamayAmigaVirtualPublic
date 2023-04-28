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
import com.internaltest.sarahchatbotmvp.ui.main.ProfileActivity
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
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
        with(btnReturn) { this?.setOnClickListener { gotoProfile() } }
        with(checkAndCancelOfferingButton) { this?.setOnClickListener { openPlaystoreAccount() } }
        uiOfferings()
    }
    private fun gotoProfile() {
        val intent = Intent(this@Wallet, ProfileActivity::class.java)
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

    @Deprecated("Deprecated in Java")
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

    //TODO corrigir bug (parece bug do qonverson) onde ele só atualiza o QProductRenewState quando
    //o usuário sai e volta pro app
    fun checkAndSwitchEntitlements() {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                val gpt4Entitlement = entitlements["gpt4_entitlement"]
                val premiumEntitlement = entitlements["premium_entitlement"]
                val liteEntitlement = entitlements["lite_entitlement"]

                if (gpt4Entitlement != null && gpt4Entitlement.isActive) {
                    // handle active Entitlement here
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.subscriptionStatus.first().let {
                            firestoreRepo.setSubscriptionStatus("GPT4")
                            hasGPT4Entitlement = true
                            firestoreRepo.subscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkEntitlements gpt4)", subscription)
                            }
                        }
                    }
                    // also you can check renew state if needed
                    // for example to check if user has canceled subscription and offer him a discount
                    when (gpt4Entitlement.renewState) {
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

                if (premiumEntitlement != null && premiumEntitlement.isActive) {
                    // handle active Entitlement here
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.subscriptionStatus.first().let {
                            firestoreRepo.setSubscriptionStatus("PREMIUM")
                            hasPremiumEntitlement = true
                            firestoreRepo.subscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkEntitlements premium)", subscription)
                            }
                        }
                    }
                    // also you can check renew state if needed
                    // for example to check if user has canceled subscription and offer him a discount
                    when (premiumEntitlement.renewState) {
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

                if (liteEntitlement != null && liteEntitlement.isActive) {
                    // handle active Entitlement here
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.subscriptionStatus.first().let {
                            firestoreRepo.setSubscriptionStatus("LITE")
                            //hasLiteEntitlement = entitlements["lite_entitlement"]?.isActive == true
                            hasLiteEntitlement = true
                            firestoreRepo.subscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkEntitlements lite)", subscription)
                                firestoreRepo.scheduleMonthlyCredits(300)
                            }
                        }
                    }
                    // also you can check renew state if needed
                    // for example to check if user has canceled subscription and offer him a discount
                    when (liteEntitlement.renewState) {
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

                if (premiumEntitlement?.isActive == false && liteEntitlement?.isActive == false
                    && gpt4Entitlement?.isActive == false) {
                    // subscription is not active
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.subscriptionStatus.first().let {
                            firestoreRepo.setSubscriptionStatus("NENHUMA")
                            firestoreRepo.subscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkEntitlements not active)", subscription)
                            }
                        }
                    }
                }

                if (liteEntitlement == null || premiumEntitlement == null || gpt4Entitlement == null)
                {
                    //fallback option in case there's an error
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.subscriptionStatus.first().let {
                            firestoreRepo.setSubscriptionStatus("NENHUMA")
                            firestoreRepo.subscriptionStatus.collect { subscription ->
                                with(subscriptionTextView) { this?.setText(subscription) }
                                Log.i("subscription (checkEntitlements not found)", subscription)
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
    private fun uiOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(offerings: QOfferings) {
                val mainOffering = offerings.main
                val GPT4Offering = offerings.offeringForID("gpt4_offering")
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

                    val GPT4OfferingTextView = findViewById<TextView>(R.id.itemNameGPT4Offering)
                    val premiumOfferingTextView = findViewById<TextView>(R.id.itemNamePremiumOffering)
                    val liteOfferingTextView = findViewById<TextView>(R.id.itemNameLiteOffering)
                    val buyCreditsTextView = findViewById<TextView>(R.id.itemName)

                    val GPT4OfferingButton = findViewById<Button>(R.id.itemPriceGPT4Offering)
                    val premiumOfferingButton = findViewById<Button>(R.id.itemPricePremiumOffering)
                    val liteOfferingButton = findViewById<Button>(R.id.itemPriceLiteOffering)
                    val buyCreditsButton = findViewById<Button>(R.id.itemPrice)

                    val GPT4OfferingDescriptionTextView = findViewById<TextView>(R.id.itemDescriptionGPT4Offering)
                    val premiumOfferingDescriptionTextView = findViewById<TextView>(R.id.itemDescriptionPremiumOffering)
                    val liteOfferingButtonDescriptionTextView = findViewById<TextView>(R.id.itemDescriptionLiteOffering)
                    val buyCreditsDescriptionTextView = findViewById<TextView>(R.id.itemDescription)

                    val GPT4OfferingInfo = GPT4Offering?.products?.get(0)
                    val premiumOfferingInfo = premiumOffering?.products?.get(0)
                    val liteOfferingInfo = liteOffering?.products?.get(0)
                    val buyCreditsInfo = buyCreditsOffering?.products?.get(0)

                    GPT4OfferingTextView.text = GPT4OfferingInfo?.skuDetail!!.title
                    premiumOfferingTextView.text = premiumOfferingInfo?.skuDetail!!.title
                    liteOfferingTextView.text = liteOfferingInfo?.skuDetail!!.title
                    buyCreditsTextView.text = buyCreditsInfo?.skuDetail!!.title

                    GPT4OfferingButton.text = GPT4OfferingInfo.skuDetail!!.price
                    premiumOfferingButton.text = premiumOfferingInfo.skuDetail!!.price
                    liteOfferingButton.text = liteOfferingInfo.skuDetail!!.price
                    buyCreditsButton.text = buyCreditsInfo.skuDetail!!.price

                    GPT4OfferingDescriptionTextView.text = GPT4OfferingInfo.skuDetail!!.description
                    premiumOfferingDescriptionTextView.text = premiumOfferingInfo.skuDetail!!.description
                    liteOfferingButtonDescriptionTextView.text = liteOfferingInfo.skuDetail!!.description
                    buyCreditsDescriptionTextView.text = buyCreditsInfo.skuDetail!!.description

                    GPT4OfferingButton.setOnClickListener {
                        purchaseOffering(GPT4Offering.products[0])
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
        Qonversion.shared.purchase(this, qProduct, callback = object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                Log.i("qproduct", qProduct.toString())

                if (qProduct.storeID == "assinatura_gpt4"
                    || qProduct.qonversionID == "assinatura_gpt4") {
                    // handle active Entitlement here
                    Log.i("qproduct gpt4", qProduct.storeID.toString())
                    updateGPT4Entitlement()
                }

                if (qProduct.storeID == "assinatura_premium"
                    || qProduct.qonversionID == "assinatura_premium") {
                    // handle active Entitlement here
                    Log.i("qproduct premium", qProduct.storeID.toString())
                    updatePremiumEntitlement()
                }

                if (qProduct.storeID == "assinatura_lite"
                    || qProduct.qonversionID == "assinatura_lite") {
                    // handle active Entitlement here
                    Log.i("qproduct lite", qProduct.storeID.toString())
                    updateLiteEntitlement()
                }

                if (qProduct.storeID == "buy_credits"
                    || qProduct.qonversionID == "buy_credits")
                {
                    addCredits(50)
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

}
