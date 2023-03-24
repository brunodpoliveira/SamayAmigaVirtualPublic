package com.internaltest.sarahchatbotmvp

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.qonversion.android.sdk.Qonversion
import androidx.datastore.preferences.core.Preferences
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QLaunchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job


class QonversionApp : Application() {
    //TODO trocar para datastore

    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "userPrefs",
        corruptionHandler = null,
        produceMigrations = {
            emptyList()
        },
        scope = CoroutineScope(Dispatchers.IO + Job())
    )


    override fun onCreate() {
        super.onCreate()
        val qonversionConfig = QonversionConfig.Builder(
            this,
            "QONVERSION_KEY_HERE",
            QLaunchMode.SubscriptionManagement).build()
        Qonversion.initialize(qonversionConfig)
        FirebaseApp.initializeApp(this)
    }
}