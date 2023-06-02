package com.internaltest.sarahchatbotmvp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QLaunchMode

class QonversionApp : Application() {
    private lateinit var firestoreRepo: FirestoreRepo

    override fun onCreate() {
        super.onCreate()
        val qonversionConfig = QonversionConfig.Builder(
            this,
            BuildConfig.QONVERSION_PROJECT_KEY,
            QLaunchMode.SubscriptionManagement).build()
        Qonversion.initialize(qonversionConfig)
        FirebaseApp.initializeApp(this)
        firestoreRepo = FirestoreRepo()
    }
}