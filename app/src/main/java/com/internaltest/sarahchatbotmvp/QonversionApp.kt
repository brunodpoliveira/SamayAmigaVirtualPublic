package com.internaltest.sarahchatbotmvp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QLaunchMode

class QonversionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val qonversionConfig = QonversionConfig.Builder(
            this,
            "5Xhi4UGTQAfqIcjpKak_Dk8fnP9iUVqb",
            QLaunchMode.SubscriptionManagement).build()
        Qonversion.initialize(qonversionConfig)
        FirebaseApp.initializeApp(this)
    }
}