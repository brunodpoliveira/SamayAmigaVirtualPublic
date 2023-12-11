package com.internaltest.sarahchatbotmvp

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.facebook.stetho.Stetho
import com.google.firebase.FirebaseApp
import com.internaltest.sarahchatbotmvp.data.AppDatabase
import com.internaltest.sarahchatbotmvp.data.UserDao
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QLaunchMode

class QonversionApp : Application() {

    companion object {
        @JvmStatic lateinit var database: AppDatabase
            private set
        lateinit var instance: QonversionApp
        lateinit var userDao: UserDao
    }

    private lateinit var firestoreRepo: FirestoreRepo

    override fun onCreate() {
        super.onCreate()

        instance = this
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "database"
        ).fallbackToDestructiveMigration().build()

        userDao = database.userDao
        userDao.getAllData().observeForever { userList ->
            Log.i("QonversionApp", "User DAO Data: $userList")
        }

        val qonversionConfig = QonversionConfig.Builder(
            this,
            BuildConfig.QONVERSION_PROJECT_KEY,
            QLaunchMode.SubscriptionManagement
        ).build()

        Qonversion.initialize(qonversionConfig)
        FirebaseApp.initializeApp(this)
        firestoreRepo = FirestoreRepo()

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
        }
    }
}