package com.internaltest.sarahchatbotmvp.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.entitlements.QEntitlementRenewState
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WalletRepo {

    private var firestoreRepo: FirestoreRepo = FirestoreRepo()

    var offerings by mutableStateOf<List<QOffering>>(emptyList())
        private set
    var hasBronzeEntitlement by mutableStateOf(false)
        private set
    var hasPrataEntitlement by mutableStateOf(false)
        private set
    var hasOuroEntitlement by mutableStateOf(false)
        private set
    var hasDiamanteEntitlement by mutableStateOf(false)
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

    fun setSubscriptionStatus(status: String) {
        when (status) {
            "BASICO" -> hasBronzeEntitlement = true
            "MEDIO" -> hasPrataEntitlement = true
            "AVANCADO" -> hasOuroEntitlement = true
            "PREMIUM" -> hasDiamanteEntitlement = true
            "NENHUMA" -> resetAllEntitlements()
        }
        // Update Firestore with the new subscription status.
        firestoreRepo.setSubscriptionStatus(status)
    }

    private fun resetAllEntitlements() {
        hasBronzeEntitlement = false
        hasPrataEntitlement = false
        hasOuroEntitlement = false
        hasDiamanteEntitlement = false
    }

    fun checkAndSwitchEntitlements(context: Context, callback: ((Map<String, QEntitlement>) -> Unit)? = null) {
        Qonversion.shared.checkEntitlements(object: QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                // Check each entitlement and update the state accordingly.
                val activeEntitlement = entitlements.values.firstOrNull { it.isActive }
                CoroutineScope(Dispatchers.IO).launch {
                    when (activeEntitlement?.id) {
                        "bronze_entitlement" -> {
                            hasBronzeEntitlement = true
                            handleRenewState(context, activeEntitlement)
                            CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("BASICO") }
                        }
                        "prata_entitlement" -> {
                            hasPrataEntitlement = true
                            handleRenewState(context, activeEntitlement)
                            CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("MEDIO") }
                        }
                        "ouro_entitlement" -> {
                            hasOuroEntitlement = true
                            handleRenewState(context, activeEntitlement)
                            CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("AVANCADO") }
                        }
                        "diamante_entitlement" -> {
                            hasDiamanteEntitlement = true
                            handleRenewState(context, activeEntitlement)
                            CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("PREMIUM") }
                        }
                        else -> {
                            CoroutineScope(Dispatchers.Main).launch { setSubscriptionStatus("NENHUMA") }
                        }
                    }
                }
                callback?.invoke(entitlements)
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
        val validEntitlementIds = setOf(
            "bronze_entitlement",
            "prata_entitlement",
            "ouro_entitlement",
            "diamante_entitlement")
        return entitlements.any { (id, entitlement) ->
            id in validEntitlementIds && entitlement.isActive
        }
    }
}