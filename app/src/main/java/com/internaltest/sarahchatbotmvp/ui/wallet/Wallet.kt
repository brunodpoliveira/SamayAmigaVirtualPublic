package com.internaltest.sarahchatbotmvp.ui.wallet

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.data.WalletRepo
import com.internaltest.sarahchatbotmvp.databinding.ActivityWalletBinding
import com.internaltest.sarahchatbotmvp.utils.DialogUtils
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Wallet : BaseActivity() {
    private lateinit var binding: ActivityWalletBinding
    private lateinit var firestoreRepo: FirestoreRepo

    private val packageNameLink = "com.internaltest.sarahchatbotmvp"

    private fun initializeWalletRepo() {
        walletRepo = WalletRepo()
    }

    private fun loadFirestoreInfo() {
        lifecycleScope.launch {
            firestoreRepo.fetchData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeWalletRepo()
        firestoreRepo = FirestoreRepo()
        walletRepo.checkAndSwitchEntitlements(this)

        configScrollToView()
        firestoreRepo = FirestoreRepo()
        uiOfferings()
        loadFirestoreInfo()
        binding.imgBtnBack.setOnClickListener {
           onBackPressedDispatcher.onBackPressed()
        }
        firestoreRepo.subscriptionStatus.asLiveData().distinctUntilChanged().observe(this)
        { subscription ->
            if (subscription != null) {
                when (subscription) {
                    "BASICO" -> {
                        binding.ctaSejaPremium.visibility = GONE
                        binding.cvBtnShopBronze.background = getDrawable(R.drawable.bg_corner_disable)
                        binding.tvBtnShopBronze.text = "Plano assinado"
                        binding.tvGerenciarAssinaturaBronze.visibility = VISIBLE
                        binding.tvGerenciarAssinaturaBronze.setOnClickListener { openPlaystoreAccount() }
                    }
                    "MEDIO" -> {
                        binding.ctaSejaPremium.visibility = GONE
                        binding.cvBtnShopPrata.background = getDrawable(R.drawable.bg_corner_disable)
                        binding.tvBtnShopPrata.text = "Plano assinado"
                        binding.tvGerenciarAssinaturaPrata.visibility = VISIBLE
                        binding.tvGerenciarAssinaturaPrata.setOnClickListener { openPlaystoreAccount() }
                    }
                    "AVANCADO" -> {
                        binding.ctaSejaPremium.visibility = GONE
                        binding.cvBtnShopOuro.background = getDrawable(R.drawable.bg_corner_disable)
                        binding.tvBtnShopOuro.text = "Plano assinado"
                        binding.tvGerenciarAssinaturaOuro.visibility = VISIBLE
                        binding.tvGerenciarAssinaturaOuro.setOnClickListener { openPlaystoreAccount() }
                    }
                    "PREMIUM" -> {
                        binding.ctaSejaPremium.visibility = GONE
                        binding.cvBtnShopDiamante.background = getDrawable(R.drawable.bg_corner_disable)
                        binding.tvBtnShop.text = "Plano assinado"
                        binding.tvGerenciarAssinaturaDiamante.visibility = VISIBLE
                        binding.tvGerenciarAssinaturaDiamante.setOnClickListener { openPlaystoreAccount() }
                    }
                }
            }
        }
        checkForActiveSubscription()
    }

    private fun configScrollToView() {
        val scrollView = binding.scrollView
        binding.cvBtnSejaPremium.setOnClickListener {
            val cardViewPosicao = IntArray(2)
            binding.textView8.getLocationOnScreen(cardViewPosicao)
            scrollView.post {
                scrollView.smoothScrollTo(0, cardViewPosicao[1] - 82)
            }
        }

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

    private fun checkForActiveSubscription() {
        walletRepo.checkAndSwitchEntitlements(this) { entitlements ->
            if (walletRepo.hasActiveSubscription(entitlements)) {
                Toast.makeText(this, "Você tem uma assinatura ativa. Cancele-a primeiro antes de fazer outra assinatura.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uiOfferings() {
        Qonversion.shared.offerings(object: QonversionOfferingsCallback {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(offerings: QOfferings) {
                val mainOffering = offerings.main
                val bronzeOffering = offerings.offeringForID("bronze_entitlement")
                val prataOffering = offerings.offeringForID("prata_entitlement")
                val ouroOffering = offerings.offeringForID("ouro_entitlement")
                val diamanteOffering = offerings.offeringForID("diamante_entitlement")

                if (mainOffering != null && mainOffering.products.isNotEmpty()) {

                    val tvPriceBronze = binding.tvValorBronze
                    val tvPricePrata = binding.tvValorPrata
                    val tvPriceOuro = binding.tvValorOuro

                    bronzeOffering?.products?.get(0)?.let {
                        tvPriceBronze.text = it.prettyPrice
                        binding.cvBtnShopBronze.setOnClickListener {
                            DialogUtils.showSubscriptionWarningPopup(this@Wallet, action = {
                                purchaseOffering(this@Wallet, bronzeOffering.products[0])
                            })
                        }
                    }

                    prataOffering?.products?.get(0)?.let {
                        tvPricePrata.text = it.prettyPrice
                        binding.cvBtnShopPrata.setOnClickListener {
                            DialogUtils.showSubscriptionWarningPopup(this@Wallet, action = {
                                purchaseOffering(this@Wallet, prataOffering.products[0])
                            })
                        }
                    }

                    ouroOffering?.products?.get(0)?.let {
                        tvPriceOuro.text = it.prettyPrice
                        binding.cvBtnShopOuro.setOnClickListener {
                            DialogUtils.showSubscriptionWarningPopup(this@Wallet, action = {
                                purchaseOffering(this@Wallet, ouroOffering.products[0])
                            })
                        }
                    }

                    diamanteOffering?.products?.get(0)?.let {

                        binding.textView11.text = "*:Grátis por 7 dias e depois ${it.prettyPrice}/mês. Renovado Automaticamente." +
                                "\nCancele quando quiser"
                        binding.cvBtnShopDiamante.setOnClickListener {
                            DialogUtils.showSubscriptionWarningPopup(this@Wallet, action = {
                                purchaseOffering(this@Wallet, diamanteOffering.products[0])
                            })
                        }
                    }
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

    fun purchaseOffering(context: Context, qProduct: QProduct) {
        walletRepo.checkAndSwitchEntitlements(this) { entitlements ->
            if (walletRepo.hasActiveSubscription(entitlements)) {
                Toast.makeText(context, "Você já possui uma assinatura ativa. Por favor, cancele a atual antes de adquirir uma nova.",
                    Toast.LENGTH_LONG).show()
            } else {
                performPurchase(context, qProduct)
            }
        }
    }

    private fun performPurchase(context: Context, qProduct: QProduct) {
        //TODO testar teste grátis
        val offerID = if (qProduct.offeringID ==
            "assinatura_diamante") "assinatura_diamante-teste-gratis" else null
        val purchaseModel = if(offerID != null) qProduct.toPurchaseModel(offerID)
        else qProduct.toPurchaseModel()

        Qonversion.shared.purchase(this@Wallet, purchaseModel, callback = object:
            QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                val entitlementToUpdate = when (qProduct.offeringID) {
                    "bronze_entitlement" -> entitlements["bronze_entitlement"]
                    "prata_entitlement" -> entitlements["prata_entitlement"]
                    "ouro_entitlement" -> entitlements["ouro_entitlement"]
                    "diamante_entitlement" -> entitlements["diamante_entitlement"]
                    else -> null
                }
                if (entitlementToUpdate != null && entitlementToUpdate.isActive) {
                    qProduct.offeringID?.let { updateSubscriptionEntitlement(context, it) }
                }
            }

            override fun onError(error: QonversionError) {
                if (error.code == QonversionErrorCode.CanceledPurchase) {
                    Toast.makeText(context, "Compra cancelada", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Erro na compra: ${error.description}",
                        Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    fun updateSubscriptionEntitlement(context: Context, entitlementId: String) {
        val status = when (entitlementId) {
            "bronze_entitlement" -> "BASICO"
            "prata_entitlement" -> "MEDIO"
            "ouro_entitlement" -> "AVANCADO"
            "diamante_entitlement" -> "PREMIUM"
            else -> throw IllegalArgumentException("Unknown entitlement ID")
        }

        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.setSubscriptionStatus(status).addOnSuccessListener {
                lifecycleScope.launch {
                    walletRepo.setSubscriptionStatus(status)
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(
                    context,
                    "Falha na atualização da assinatura: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
