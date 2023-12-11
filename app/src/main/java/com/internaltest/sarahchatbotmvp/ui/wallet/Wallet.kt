package com.internaltest.sarahchatbotmvp.ui.wallet

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.data.WalletRepo
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import com.internaltest.sarahchatbotmvp.utils.DialogUtils
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class Wallet : BaseActivity() {
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

    private fun initializeWalletRepo() {
        walletRepo = WalletRepo()
    }

    private fun loadFirestoreInfo() {
        lifecycleScope.launch {
            walletRepo.getCredits().flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { credits ->
                    with(creditsTextView) { this?.text = credits.toString() }
                    Log.i("credits (load data store)", credits.toString())
                }
        }

        // Observe subscription status changes
        firestoreRepo.subscriptionStatus.asLiveData().distinctUntilChanged().observe(this)
        { subscription ->
            with(subscriptionTextView) { this?.text = subscription }
            Log.i("subscription (load data store)", subscription)
        }
        lifecycleScope.launch {
            firestoreRepo.fetchData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        initializeWalletRepo()
        firestoreRepo = FirestoreRepo()
        walletRepo.checkAndSwitchEntitlements(this)
        creditsHeadTextView = findViewById(R.id.creditsHead)
        subscriptionHeadTextView = findViewById(R.id.subscriptionHead)
        btnReturn = findViewById(R.id.goBack)
        checkAndCancelOfferingButton = findViewById(R.id.itemCancel)
        with(btnReturn) { this?.setOnClickListener { gotoMainActivity() } }
        with(checkAndCancelOfferingButton) { this?.setOnClickListener { openPlaystoreAccount() } }
        uiOfferings()
        if (intent.getBooleanExtra("SHOW_BUY_CREDITS", false)) {
            purchaseBuyCreditsOfferings()
        }
        loadFirestoreInfo()
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
            FirebaseCrashlytics.getInstance().recordException(e)
            Toast.makeText(
                applicationContext,
                "Erro durante processo",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }

    @Deprecated("update")
    override fun onBackPressed() {
        super.onBackPressed()
        Toast.makeText(applicationContext,
            "Botão voltar desligado. Use as opções do menu", Toast.LENGTH_LONG).show()
    }

    private fun uiOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(offerings: QOfferings) {
                val mainOffering = offerings.main
                val gpt4Offering = offerings.offeringForID("gpt4_offering")
                val buyCreditsOffering = offerings.offeringForID("buy_credits_offering")

                if (mainOffering != null && mainOffering.products.isNotEmpty()) {
                    Log.i("test offerings", mainOffering.products.toString())
                    subscriptionTextView = findViewById(R.id.subscriptionStatus)
                    subscriptionTextView?.text = subscription.toString()
                    creditsTextView = findViewById(R.id.creditsCount)
                    creditsTextView?.text = credits.toString()
                    Log.i("credits text view", creditsTextView.toString())

                    val gpt4OfferingTextView = findViewById<TextView>(R.id.itemNameGPT4Offering)
                    val buyCreditsTextView = findViewById<TextView>(R.id.itemName)

                    val gpt4OfferingButton = findViewById<Button>(R.id.itemPriceGPT4Offering)
                    val buyCreditsButton = findViewById<Button>(R.id.itemPrice)

                    val gpt4OfferingDescriptionTextView =
                        findViewById<TextView>(R.id.itemDescriptionGPT4Offering)
                    val buyCreditsDescriptionTextView = findViewById<TextView>(R.id.itemDescription)

                    val gpt4OfferingInfo = gpt4Offering?.products?.get(0)
                    val buyCreditsInfo = buyCreditsOffering?.products?.get(0)

                    gpt4OfferingTextView.text = gpt4OfferingInfo?.skuDetail!!.title
                    buyCreditsTextView.text = buyCreditsInfo?.skuDetail!!.title

                    gpt4OfferingButton.text = gpt4OfferingInfo.skuDetail!!.price
                    buyCreditsButton.text = buyCreditsInfo.skuDetail!!.price

                    gpt4OfferingDescriptionTextView.text = gpt4OfferingInfo.skuDetail!!.description
                    buyCreditsDescriptionTextView.text = buyCreditsInfo.skuDetail!!.description

                    gpt4OfferingButton.setOnClickListener {
                        purchaseOffering(this@Wallet, gpt4Offering.products[0])
                        DialogUtils.showSubscriptionWarningPopup(this@Wallet)

                    }
                    buyCreditsButton.setOnClickListener {
                        purchaseOffering(this@Wallet, buyCreditsOffering.products[0])
                    }
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

    fun purchaseOffering(context: Context, qProduct: QProduct) {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                if (walletRepo.hasActiveSubscription(entitlements)) {
                    Toast.makeText(context,
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
                                walletRepo.updateGPT4Entitlement(context)
                            }

                            if (qProduct.storeID == "buy_credits"
                                || qProduct.qonversionID == "buy_credits")
                            {
                                walletRepo.addCredits(75)
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

    private fun purchaseBuyCreditsOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) {
                val buyCreditsOffering = offerings.offeringForID("buy_credits_offering")
                buyCreditsOffering?.let {
                    purchaseOffering(this@Wallet,it.products[0])
                }
            }

            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }
}
