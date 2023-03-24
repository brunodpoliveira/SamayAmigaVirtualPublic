package com.internaltest.sarahchatbotmvp.wallet

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.data.DataStoreRepo
import com.internaltest.sarahchatbotmvp.login.ProfileActivity
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

class Wallet : ComponentActivity(){
    lateinit var dataStoreRepo: DataStoreRepo
    private val credits: Flow<Int> by lazy { dataStoreRepo.getCredits }
    private val subscription: Flow<String> by lazy { dataStoreRepo.getSubscriptionStatus }
    private var subscriptionHeadTextView: TextView? = null
    private var creditsHeadTextView: TextView? = null
    var subscriptionTextView: TextView? = null
    var creditsTextView: TextView? = null
    private var btnReturn: TextView? = null
    private var checkAndCancelOfferingButton: TextView? = null

    private val packageNameLink = "com.internaltest.sarahchatbotmvp"
    var offerings by mutableStateOf<List<QOffering>>(emptyList())
        private set
    var hasPremiumPermission by mutableStateOf(false)
        private set
    var hasLitePermission by mutableStateOf(false)
        private set

    init {
        loadOfferings()
        loadDataStoreInfo()
        checkAndSwitchPermissions()
    }

    private fun loadDataStoreInfo() {
        CoroutineScope(Dispatchers.Main).launch {
            dataStoreRepo.getCredits.collect { credits ->
                with(creditsTextView) { this?.setText(credits.toString()) }
                Log.i("credits (load data store wallet)", credits.toString())
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            dataStoreRepo.getSubscriptionStatus.collect { subscription ->
                with(subscriptionTextView) { this?.setText(subscription) }
                Log.i("subscription (load data store wallet)", subscription)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        dataStoreRepo = DataStoreRepo(this)
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

    private fun updatePremiumPermission() {
        Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                hasPremiumPermission = entitlements["premium_permission"]?.isActive == true
                Log.d(ContentValues.TAG, "permissions: ${entitlements.keys.toList()}")
                Log.i("subscription (update premium)", entitlements.toString())
                checkAndSwitchPermissions()
            }
        })
    }

    private fun updateLitePermission() {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                hasLitePermission = entitlements["lite_permission"]?.isActive == true
                Log.d(ContentValues.TAG, "permissions: ${entitlements.keys.toList()}")
                Log.i("subscription (update lite)", entitlements.toString())
                checkAndSwitchPermissions()
            }
        })
    }

    fun addCredits(newCredits: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            dataStoreRepo.getCredits.first().let { currentCredits ->
                dataStoreRepo.setCredits(currentCredits + newCredits)
            }
        }
        checkAndSwitchPermissions()
    }

    //TODO corrigir bug (parece bug do qonverson) onde ele só atualiza o QProductRenewState quando
    //o usuário sai e volta pro app
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
                                        "Clique no botão checar assinatura para mais informações",
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
                                        "Clique no botão checar assinatura para mais informações",
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
    private fun uiOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(offerings: QOfferings) {
                val mainOffering = offerings.main
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

                    val premiumOfferingTextView = findViewById<TextView>(R.id.itemNamePremiumOffering)
                    val liteOfferingTextView = findViewById<TextView>(R.id.itemNameLiteOffering)
                    val buyCreditsTextView = findViewById<TextView>(R.id.itemName)

                    val premiumOfferingButton = findViewById<Button>(R.id.itemPricePremiumOffering)
                    val liteOfferingButton = findViewById<Button>(R.id.itemPriceLiteOffering)
                    val buyCreditsButton = findViewById<Button>(R.id.itemPrice)

                    val premiumOfferingDescriptionTextView = findViewById<TextView>(R.id.itemDescriptionPremiumOffering)
                    val liteOfferingButtonDescriptionTextView = findViewById<TextView>(R.id.itemDescriptionLiteOffering)
                    val buyCreditsDescriptionTextView = findViewById<TextView>(R.id.itemDescription)

                    val premiumOfferingInfo = premiumOffering?.products?.get(0)
                    val liteOfferingInfo = liteOffering?.products?.get(0)
                    val buyCreditsInfo = buyCreditsOffering?.products?.get(0)

                    premiumOfferingTextView.text = premiumOfferingInfo?.skuDetail!!.title
                    liteOfferingTextView.text = liteOfferingInfo?.skuDetail!!.title
                    buyCreditsTextView.text = buyCreditsInfo?.skuDetail!!.title

                    premiumOfferingButton.text = premiumOfferingInfo.skuDetail!!.price
                    liteOfferingButton.text = liteOfferingInfo.skuDetail!!.price
                    buyCreditsButton.text = buyCreditsInfo.skuDetail!!.price

                    premiumOfferingDescriptionTextView.text = premiumOfferingInfo.skuDetail!!.description
                    liteOfferingButtonDescriptionTextView.text = liteOfferingInfo.skuDetail!!.description
                    buyCreditsDescriptionTextView.text = buyCreditsInfo.skuDetail!!.description

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

                if (qProduct.storeID == "assinatura_premium"
                    || qProduct.qonversionID == "assinatura_premium") {
                    // handle active permission here
                    Log.i("qproduct premium", qProduct.storeID.toString())
                    updatePremiumPermission()
                }

                if (qProduct.storeID == "assinatura_lite"
                    || qProduct.qonversionID == "assinatura_lite") {
                    // handle active permission here
                    Log.i("qproduct lite", qProduct.storeID.toString())
                    updateLitePermission()
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
