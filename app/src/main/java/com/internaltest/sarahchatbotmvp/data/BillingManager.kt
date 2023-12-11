package com.internaltest.sarahchatbotmvp.data

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

//NÃO DELETAR
class BillingManager(private val context: Context, private val userId: String) {
    private lateinit var billingClient: BillingClient

    private val purchaseUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePromoCodePurchase(purchase)
                }
            }
        } else {
            Log.e("Billing", "onPurchasesUpdated failed: ${billingResult.debugMessage}")
        }
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchaseUpdateListener)
            .enablePendingPurchases()
            .build()
        startBillingConnection()
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i("Billing", "Billing client connected successfully")
                    queryPromoCodePurchases()
                } else {
                    Log.e(
                        "Billing",
                        "Billing client connection failed: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("Billing", "Billing client disconnected")
            }
        })
    }

    private fun queryPromoCodePurchases() {
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchasesList.isEmpty()) {
                    Log.i("Billing", "No purchases found")
                } else {
                    Log.i("Billing", "Purchases found: ${purchasesList.size}")
                    for (purchase in purchasesList) {
                        handlePromoCodePurchase(purchase)
                    }
                }
            } else {
                Log.e("Billing", "Query purchases failed: ${billingResult.responseCode}")
            }
        }
    }

    private fun handlePromoCodePurchase(purchase: Purchase) {
        // Extract necessary information from the purchase object
        val packageName = context.packageName
        val productId = purchase.skus.firstOrNull() ?: return
        val purchaseToken = purchase.purchaseToken
        val userId = userId // Replace this with the actual user ID

        // Create JSON payload
        val payload = JSONObject()
        payload.put("packageName", packageName)
        payload.put("productId", productId)
        payload.put("purchaseToken", purchaseToken)
        payload.put("userId", userId)

        Log.i("payload", payload.toString())

        Firebase.auth.currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val idToken = task.result?.token
                // Send purchase information to the Firestore function
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://us-central1-samay-amiga-virtual-514f2.cloudfunctions.net/handleDeveloperNotification")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(
                        payload.toString()
                            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    )
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(
                            "Firestore Function",
                            "Request to handleDeveloperNotification failed", e
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            Log.i(
                                "Firestore Function",
                                "handleDeveloperNotification processed successfully"
                            )
                        } else {
                            Log.e("Firestore Function", "Failed to get ID token", task.exception)
                        }
                    }
                })
            }
        }
    }
}