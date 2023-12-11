package com.internaltest.sarahchatbotmvp.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.internaltest.sarahchatbotmvp.data.UserDocument.userDocument
import com.internaltest.sarahchatbotmvp.utils.InactiveNotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.math.log

class FirestoreRepo {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    //lateinit var userDocListenerRegistration: ListenerRegistration
    private val MIN_UPDATE_INTERVAL = TimeUnit.HOURS.toMillis(6)
    private val NOTIFICATION_CHECK_INTERVAL_HOURS = 6L

    private val crashlytics = FirebaseCrashlytics.getInstance()

    private var lastUpdateTimestamp: Long? = null

    private val cache = Cache<String, Any>()

    // MutableStateFlow for subscription status, credits, dark mode, and daily login
    private val _subscriptionStatus = MutableStateFlow("NENHUMA")
    val subscriptionStatus: Flow<String> = _subscriptionStatus.asStateFlow()

    private val _credits = MutableStateFlow(0)
    val credits: Flow<Int> = _credits.asStateFlow()

    private val _totalMsgs = MutableStateFlow(0)
    val totalMsgs: Flow<Int> = _totalMsgs.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: Flow<Boolean> = _darkMode.asStateFlow()

    private val _textToSpeech = MutableStateFlow(false)
    val textToSpeech: Flow<Boolean> = _textToSpeech.asStateFlow()

    private val _dailyLoginDay = MutableStateFlow("")
    val dailyLoginDay: Flow<String> = _dailyLoginDay.asStateFlow()

    val _fontSize = MutableStateFlow(20) // Assuming 20 is the default font size

    init{
        CoroutineScope(Dispatchers.IO).launch {
            fetchData()
        }
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(TimeUnit.HOURS.toMillis(1))
                refreshCache()
            }
        }
    }

    suspend fun fetchData() {
        try {
            fetchSubscriptionStatus()
            val initialCredits = fetchCredits()
            _credits.value = initialCredits
            cache.put("credits", initialCredits)
            val initialMsgs = fetchTotalMsgs()
            _totalMsgs.value = initialMsgs
            cache.put("initialMsgs", initialMsgs)
            fetchDarkMode()
            //observeDarkMode()
            fetchTextToSpeech()
            fetchDailyLoginReward()
            fetchFontSize()
        } catch (e: FirebaseFirestoreException) {
            crashlytics.recordException(e)
            Log.e("fetchData", "Error fetching data from Firestore", e)
        }
    }

    private suspend fun refreshCache() {
        try {
            if (userDocument == null) {
                getUserDocument()
            }
            val userDocument = userDocument
            if (userDocument != null) {
                val userData = userDocument.get().await()
                if (userData.exists()) {
                    val subscriptionStatus = userData.getString("subscription_status") ?: "NENHUMA"
                    _subscriptionStatus.value = subscriptionStatus
                    cache.put("subscriptionStatus", subscriptionStatus)

                    val credits = userData.getLong("credits")?.toInt() ?: 0
                    _credits.value = credits
                    cache.put("credits", credits)

                    val totalMsgs = userData.getLong("total_messages_sent")?.toInt() ?: 0
                    _totalMsgs.value = totalMsgs
                    cache.put("totalMsgs", totalMsgs)

                    val darkModeStatus = userData.getBoolean("dark_mode") ?: false
                    _darkMode.value = darkModeStatus
                    cache.put("darkMode", darkModeStatus)

                    val textToSpeechStatus = userData.getBoolean("text_to_speech") ?: false
                    _textToSpeech.value = textToSpeechStatus
                    cache.put("textToSpeech", textToSpeechStatus)

                    val dailyLoginDay = userData.getString("daily_login_day") ?: ""
                    _dailyLoginDay.value = dailyLoginDay
                    cache.put("dailyLoginDay", dailyLoginDay)

                    val fontSize = userData.getLong("font_size")?.toInt() ?: 20
                    _fontSize.value = fontSize
                    cache.put("fontSize", fontSize)
                }
            }
        } catch (e: FirebaseFirestoreException) {
            crashlytics.recordException(e)
            Log.e("refreshCache", "Error fetching data from Firestore", e)
        }
    }

    fun getUserDocument(): DocumentReference? {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("getUserDocument", "User not logged in")
            return null
        }
        val userDocument = firestore.collection("users").document(currentUser.uid)
        Firebase.auth.currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val idToken = task.result?.token
                Log.i("Firebase Auth", "ID Token: $idToken")
            } else {
                Log.e("Firebase Auth", "Failed to get ID token", task.exception)
            }
        }
        Log.i("getUserDocument", "User document path: ${userDocument.path}")

        UserDocument.userDocument = userDocument
        return userDocument
    }

    private suspend fun fetchSubscriptionStatus(): String {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                if (cache.containsKey("subscription_status")) {
                    observeSubscriptionStatus()
                    return cache.get("subscription_status") as String
                } else {
                    val subscription = userDocument.get().await()["subscription_status"] as? String
                    if (subscription != null) {
                        cache.put("subscription_status", subscription)
                        observeSubscriptionStatus()
                        return subscription
                    }
                }
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("fetchSubscriptionStatus", "Error fetching subscription status", e)
            }
        }
        return "NENHUMA"
    }

    private fun observeSubscriptionStatus() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        userDocument?.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("observeSubscriptionStatus", "Error observing SubscriptionStatus", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                Log.i("observeSubscriptionStatus", "observeSubscriptionStatus no error reached and called")
                val subscriptionStatus = snapshot.getString("subscription_status") ?: "NENHUMA"
                _subscriptionStatus.value = subscriptionStatus
                cache.put("subscription_status", subscriptionStatus)
            }
        }
    }

    suspend fun setSubscriptionStatus(newStatus: String) {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                userDocument.update("subscription_status", newStatus).await()
                cache.put("subscription_status", newStatus)
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("subscription_status", "Error setting subscription status", e)
            }
        }
    }

    private suspend fun fetchCredits(): Int {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                return if (cache.containsKey("credits")) {
                    observeCredits()
                    cache.get("credits") as Int
                } else {
                    val userData = userDocument.get().await()
                    val fetchedCredits = userData.get("credits") as? Long
                    val credits = fetchedCredits?.toInt() ?: 0
                    cache.put("credits", credits)
                    observeCredits()
                    credits
                }
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("fetchCredits", "Error fetching credits", e)
            }
        }
        return 0
    }

    private fun observeCredits() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        userDocument?.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("observeCredits", "Error observing Credits", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                Log.i("observeCredits", "observeCredits no error reached and called")
                val credits = snapshot.getLong("credits") ?: 0
                cache.put("credits", credits)
            }
        }
    }

    suspend fun setCredits(newCredits: Int) {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                userDocument.update("credits", newCredits).await()
                cache.put("credits", newCredits)
                Log.i("setCredits", "Updated cache: ${cache.get("credits")}")
                _credits.value = newCredits
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("setCredits", "Error setting credits", e)
            }
        }
    }

    //TODO modificar para ser assinatura
    /*
    fun startListeningForCreditUpdates() {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            userDocListenerRegistration = userDocument.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error listening for credit updates: $error")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val fetchedCredits = snapshot.get("credits") as? Long
                    Log.e("Firestore startListeningForCreditUpdates",fetchedCredits.toString())
                    // Update the app's UI with the new credit balance
                    CoroutineScope(Dispatchers.Main).launch {
                        setCredits(fetchedCredits?.toInt()!!)
                        }
                    }
                }
            }
        }
     */

    private suspend fun fetchTotalMsgs(): Int {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                val cacheKey = "totalMsgs"
                return if (cache.containsKey(cacheKey)) {
                    cache.get(cacheKey) as Int
                } else {
                    val userData = userDocument.get().await()
                    val fetchedTotalMsgs = userData.getLong("total_messages_sent")?.toInt() ?: 0
                    cache.put(cacheKey, fetchedTotalMsgs)
                    _totalMsgs.value = fetchedTotalMsgs
                    fetchedTotalMsgs
                }
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("fetchTotalMsgs", "Error fetching total messages sent", e)
            }
        }
        return 0
    }

    suspend fun setTotalMsgs(newMsgs: Int) {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                userDocument.update("total_messages_sent", newMsgs).await()
                val cacheKey = "totalMsgs"
                cache.put(cacheKey, newMsgs)
                _totalMsgs.value = newMsgs
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("setTotalMsgs", "Error setting total messages sent", e)
            }
        }
    }

    private suspend fun fetchTextToSpeech() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                val cacheKey = "textToSpeech"
                if (cache.containsKey(cacheKey)) {
                    _textToSpeech.value = cache.get(cacheKey) as Boolean
                    observeTextToSpeech()
                } else {
                    val textToSpeechStatus = userDocument.get().await()["text_to_speech"] as? Boolean
                    if (textToSpeechStatus != null) {
                        _textToSpeech.value = textToSpeechStatus
                        cache.put(cacheKey, textToSpeechStatus)
                        observeTextToSpeech()
                    }
                }
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("fetchTextToSpeech", "Error fetching text-to-speech status", e)
            }
        }
    }

    private fun observeTextToSpeech() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        userDocument?.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("observeTextToSpeech", "Error observing text-to-speech", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val textToSpeechStatus = snapshot.getBoolean("text_to_speech") ?: false
                _textToSpeech.value = textToSpeechStatus
                cache.put("textToSpeech", textToSpeechStatus)
            }
        }
    }

    suspend fun setTextToSpeech(newStatus: Boolean): Boolean {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        return if (userDocument != null) {
            try {
                userDocument.update("text_to_speech", newStatus).await()
                _textToSpeech.value = newStatus
                val cacheKey = "textToSpeech"
                cache.put(cacheKey, newStatus)
                true
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("setTextToSpeech", "Error setting text-to-speech status", e)
                false
            }
        } else {
            false
        }
    }

    private suspend fun fetchDarkMode() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                val cacheKey = "darkMode"
                if (cache.containsKey(cacheKey)) {
                    _darkMode.value = cache.get(cacheKey) as Boolean
                    observeDarkMode()
                } else {
                    val darkModeStatus = userDocument.get().await()["dark_mode"] as? Boolean
                    if (darkModeStatus != null) {
                        _darkMode.value = darkModeStatus
                        cache.put(cacheKey, darkModeStatus)
                        observeDarkMode()
                    }
                }
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("fetchDarkMode", "Error fetching dark mode status", e)
            }
        }
    }

    private fun observeDarkMode() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        userDocument?.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("observeDarkMode", "Error observing dark mode", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                Log.i("observeDarkMode", "observeDarkMode no error reached and called")
                val darkModeStatus = snapshot.getBoolean("dark_mode") ?: false
                _darkMode.value = darkModeStatus
                cache.put("darkMode", darkModeStatus)
            }
        }
    }

    suspend fun setDarkMode(newStatus: Boolean): Boolean {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        return if (userDocument != null) {
            try {
                userDocument.update("dark_mode", newStatus).await()
                _darkMode.value = newStatus
                val cacheKey = "darkMode"
                cache.put(cacheKey, newStatus)
                true
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("setDarkMode", "Error setting dark mode status", e)
                false
            }
        } else {
            false
        }
    }

    fun initUserDocumentAsync(user: FirebaseUser): Deferred<Boolean> {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            return CoroutineScope(Dispatchers.IO).async {
                try {
                    val snapshot = userDocument.get().await()
                    if (!snapshot.exists()) {
                        // Set default values for the user document
                        val defaultData = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "name" to user.displayName,
                            "photo_url" to user.photoUrl?.toString(),
                            "subscription_status" to "NENHUMA",
                            "credits" to 0,
                            "total_messages_sent" to 0,
                            "dark_mode" to false,
                            "text_to_speech" to false,
                            "daily_login_day" to "",
                            "last_active" to "",
                            "last_notification_check" to "",
                            "last_credit_date" to "",
                            "font_size" to 20
                        )
                        userDocument.set(defaultData, SetOptions.merge()).await() // Force sync
                        fetchData()
                    }
                    true
                } catch (e: Exception) {
                    crashlytics.recordException(e)
                    Log.e("initUserDocumentAsync", "Error initializing user document", e)
                    false
                }
            }
        } else {
            return CoroutineScope(Dispatchers.IO).async { false }
        }
    }

    private suspend fun fetchDailyLoginReward() {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                val dailyLoginDayValue = userDocument.get().await()["daily_login_day"] as? String
                if (dailyLoginDayValue != null) {
                    _dailyLoginDay.value = dailyLoginDayValue
                }
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("fetchDailyLoginReward", "Error fetching daily login reward", e)
            }
        }
    }

    suspend fun setDailyLoginDay(todayString: String) {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                userDocument.update("daily_login_day", todayString).await()
                _dailyLoginDay.value = todayString
                Log.i("setDailyLoginDay", "Updated daily login day to $todayString")
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("setDailyLoginDay", "Error setting daily login day", e)
            }
        }
    }

    private suspend fun updateUserActiveTimestamp() {
        if (userDocument == null) {
            getUserDocument()
        }
        val currentTime = System.currentTimeMillis()
        if (lastUpdateTimestamp == null || currentTime - lastUpdateTimestamp!! >= MIN_UPDATE_INTERVAL) {
            val userDocument = userDocument ?: return
            try {
                val serverTimestamp = FieldValue.serverTimestamp()
                userDocument.update(
                    mapOf(
                        "last_active" to serverTimestamp,
                        "last_notification_check" to serverTimestamp
                    )
                ).await()
                lastUpdateTimestamp = currentTime
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("updateUserActiveTimestamp", "Error updating user active timestamp", e)
            }
        }
    }

    suspend fun onUserActivity(context: Context) {
        try {
            updateUserActiveTimestamp()
            // Schedule the periodic worker to check for inactive notifications every 6 hours
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val notificationCheckRequest = PeriodicWorkRequestBuilder<InactiveNotificationWorker>(
                NOTIFICATION_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    "inactive_notification_check",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    notificationCheckRequest
                )
        } catch (e: FirebaseFirestoreException) {
            crashlytics.recordException(e)
            Log.e("onUserActivity", "Error updating user activity", e)
        }
    }

    fun fetchFontSize(): Flow<Int> = callbackFlow {
        val cacheKey = "fontSize"
        if (cache.containsKey(cacheKey)) {
            val cachedFontSize = cache.get(cacheKey) as Int
            trySend(cachedFontSize).isSuccess
        } else {
            val userDocument = userDocument
            val listener = userDocument?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("fetchFontSize", "Listen failed.", error)
                }
                if (snapshot != null && snapshot.exists()) {
                    val fontSize = snapshot.getLong("font_size")
                    if (fontSize != null) {
                        try {
                            val fontSizeValue = fontSize.toInt()
                            trySend(fontSizeValue).isSuccess
                            cache.put(cacheKey, fontSizeValue)
                        } catch (e: Exception) {
                            crashlytics.recordException(e)
                            Log.e("fetchFontSize", "Error sending data", e)
                        }
                    } else {
                        // Font size not found, add the field with a default value of 20
                        try {
                            userDocument.update("font_size", 20).addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    trySend(20).isSuccess
                                    cache.put(cacheKey, 20)
                                } else {
                                    Log.e("fetchFontSize", "Error adding font size", task.exception)
                                    close() // close the flow on error
                                }
                            }
                        } catch (e: Exception) {
                            crashlytics.recordException(e)
                            Log.e("fetchFontSize", "Error adding font size", e)
                            close() // close the flow on error
                        }
                    }
                }
            }
            awaitClose {
                listener?.remove()
            }
        }
    }

    suspend fun setFontSize(newSize: Int) {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        userDocument?.let { doc ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    doc.update("font_size", newSize).await()
                    _fontSize.emit(newSize)
                    val cacheKey = "fontSize"
                    cache.put(cacheKey, newSize)
                    Log.i("setFontSize", newSize.toString())
                } catch (e: FirebaseFirestoreException) {
                    crashlytics.recordException(e)
                    Log.e("setFontSize", "Error setting font size", e)
                }
            }
        } ?: run {
            Log.e("setFontSize", "User document is null")
        }
    }

    suspend fun saveFcmToken(token: String) {
        if (userDocument == null) {
            getUserDocument()
        }
        val userDocument = userDocument
        if (userDocument != null) {
            try {
                userDocument.update("fcm_token", token).await()
            } catch (e: FirebaseFirestoreException) {
                crashlytics.recordException(e)
                Log.e("saveFcmToken", "Error saving FCM token", e)
            }
        }
    }


}
