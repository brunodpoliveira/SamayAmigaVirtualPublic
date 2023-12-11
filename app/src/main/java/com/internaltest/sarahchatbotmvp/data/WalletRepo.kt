package com.internaltest.sarahchatbotmvp.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QEntitlementRenewState
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WalletRepo {

    private var firestoreRepo: FirestoreRepo = FirestoreRepo()

    var offerings by mutableStateOf<List<QOffering>>(emptyList())
        private set
    var hasGPT4Entitlement by mutableStateOf(false)
        private set

    init {
        loadOfferings()
    }

    private fun loadOfferings() {
        Qonversion.shared.offerings(
            object : QonversionOfferingsCallback {
                override fun onError(error: QonversionError) {
                    // Handle error
                    Log.d(ContentValues.TAG, "onError: ${error.description}")
                }
                override fun onSuccess(offerings: QOfferings) {
                    this@WalletRepo.offerings = offerings.availableOfferings
                }
            }
        )
    }

    fun getCredits(): Flow<Int> {
        return firestoreRepo.credits
    }

    fun addCredits(newCredits: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.credits.first().let { currentCredits ->
                firestoreRepo.setCredits(currentCredits + newCredits)
            }
        }
    }

    fun setCredits(newCredits: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.setCredits(newCredits)
        }
    }

    fun setCreditsToZero() {
        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.credits.first().let {
                firestoreRepo.setCredits(0)
            }
        }
    }

    fun updateGPT4Entitlement(context: Context) {
        Qonversion.shared.checkEntitlements(object : QonversionEntitlementsCallback {
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                hasGPT4Entitlement = entitlements["gpt4_Entitlement"]?.isActive == true
                Log.d(ContentValues.TAG, "Entitlements: ${entitlements.keys.toList()}")
                Log.i("subscription (update gpt4)", entitlements.toString())
                checkAndSwitchEntitlements(context)
            }
        })
    }

    suspend fun setSubscriptionStatus(status: String) {
        firestoreRepo.setSubscriptionStatus(status)
    }

    //TODO corrigir bug (parece bug do qonverson) onde ele só atualiza o QProductRenewState quando
    //o usuário sai e volta pro app
    fun checkAndSwitchEntitlements(context: Context) {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                if (entitlements["gpt4_entitlement"]?.isActive == true) {
                    hasGPT4Entitlement = true
                    handleRenewState(context,entitlements["gpt4_entitlement"])
                    CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("GPT4") }
                } else {
                    CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("NENHUMA") }
                }
            }
            override fun onError(error: QonversionError) {
                Log.d(ContentValues.TAG, "onError: ${error.description}")
            }
        })
    }

    private fun handleRenewState(context: Context, entitlement: QEntitlement?) {
        // Handle the renew state if needed
        when (entitlement?.renewState) {
            QEntitlementRenewState.WillRenew -> {
                // Subscription will renew
                Log.i("renew state", "will renew")
            }
            QEntitlementRenewState.NonRenewable -> {
                // Subscription won't renew
                Toast.makeText(
                    context,
                    "Produto Não Renovável",
                    Toast.LENGTH_LONG
                ).show()
            }
            QEntitlementRenewState.BillingIssue -> {
                // Issue with billing
            }
            QEntitlementRenewState.Canceled ->{
                Toast.makeText(
                    context,
                    "Assinatura cancelada, mas ainda ativa até o fim do ciclo de " +
                            "faturamento atual ", Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                // Do nothing, subscription has expired
            }
        }
    }
    fun hasActiveSubscription(entitlements: Map<String, QEntitlement>): Boolean {
        return entitlements.any {
            (((it.value.productId == "assinatura_gpt4")) && it.value.isActive)
        }
    }

}