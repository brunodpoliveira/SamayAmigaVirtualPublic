package com.internaltest.sarahchatbotmvp.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
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

    private val _totalMsgs = MutableStateFlow(0)
    val totalMsgs: Flow<Int> = _totalMsgs.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: Flow<Boolean> = _darkMode.asStateFlow()

    private val _textToSpeech = MutableStateFlow(false)
    val textToSpeech: Flow<Boolean> = _textToSpeech.asStateFlow()

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
            val initialMsgs = fetchTotalMsgs()
            _totalMsgs.value = initialMsgs
            cache.put("initialMsgs", initialMsgs)
            fetchDarkMode()
            fetchTextToSpeech()
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

                    val totalMsgs = userData.getLong("total_messages_sent")?.toInt() ?: 0
                    _totalMsgs.value = totalMsgs
                    cache.put("totalMsgs", totalMsgs)

                    val darkModeStatus = userData.getBoolean("dark_mode") ?: false
                    _darkMode.value = darkModeStatus
                    cache.put("darkMode", darkModeStatus)

                    val textToSpeechStatus = userData.getBoolean("text_to_speech") ?: false
                    _textToSpeech.value = textToSpeechStatus
                    cache.put("textToSpeech", textToSpeechStatus)

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

    fun setSubscriptionStatus(newStatus: String): Task<Void> {
        val userDocument = getUserDocument()
        return userDocument?.update("subscription_status", newStatus) ?: Tasks.forException(
            FirebaseFirestoreException("User document not found", FirebaseFirestoreException.Code.ABORTED))
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
                            "total_messages_sent" to 0,
                            "dark_mode" to false,
                            "text_to_speech" to false,
                            "last_active" to "",
                            "last_notification_check" to "",
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
