package com.internaltest.sarahchatbotmvp.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.internaltest.sarahchatbotmvp.utils.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class FirestoreRepo {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var lastCreditDate : LocalDateTime? = null
    lateinit var userDocListenerRegistration: ListenerRegistration

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

    init{
        CoroutineScope(Dispatchers.IO).launch {
            fetchData()
        }
    }

    suspend fun fetchData() {
        try {
            fetchSubscriptionStatus()
            val initialCredits = fetchCredits()
            _credits.value = initialCredits
            val initialMsgs = fetchTotalMsgs()
            _totalMsgs.value = initialMsgs
            fetchDarkMode()
            fetchTextToSpeech()
            fetchDailyLoginReward()
        } catch (e: FirebaseFirestoreException) {
            Log.e("fetchData", "Error fetching data from Firestore", e)
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

        return userDocument
    }

    fun getUserId(): String? {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("getUserId", "User not logged in")
            return null
        }
        Log.i("getUserId", currentUser.uid)
        return currentUser.uid
    }

    private suspend fun fetchSubscriptionStatus() {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                val subscription = userDocument.get().await()["subscription_status"] as? String
                if (subscription != null) {
                    _subscriptionStatus.value = subscription
                }
            } catch (e: FirebaseFirestoreException) {
                Log.e("fetchSubscriptionStatus", "Error fetching subscription status", e)
            }
        }
    }

    suspend fun setSubscriptionStatus(newStatus: String) {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                userDocument.update("subscription_status", newStatus).await()
                _subscriptionStatus.value = newStatus
            } catch (e: FirebaseFirestoreException) {
                Log.e("setSubscriptionStatus", "Error setting subscription status", e)
            }
        }
    }

    private suspend fun fetchCredits(): Int {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                val userData = userDocument.get().await()
                Log.i("fetchCredits", "Snapshot data: ${userData.data}")
                val fetchedCredits = userData.get("credits") as? Long
                Log.i("fetchCredits", "Fetched credits: $fetchedCredits")
                return fetchedCredits?.toInt() ?: 0
            } catch (e: FirebaseFirestoreException) {
                Log.e("fetchCredits", "Error fetching credits", e)
            }
        }
        return 0
    }

    suspend fun setCredits(newCredits: Int) {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                userDocument.update("credits", newCredits).await()
                _credits.value = newCredits
            } catch (e: FirebaseFirestoreException) {
                Log.e("setCredits", "Error setting credits", e)
            }
        }
    }

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

    private suspend fun fetchTotalMsgs(): Int {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                val userData = userDocument.get().await()
                Log.i("fetchTotalMsgs", "Snapshot data: ${userData.data}")
                val fetchedTotalMsgs = userData.get("total_messages_sent") as? Long
                Log.i("fetchedTotalMsgs", "Fetched TotalMsgs: $fetchedTotalMsgs")
                return fetchedTotalMsgs?.toInt() ?: 0
            } catch (e: FirebaseFirestoreException) {
                Log.e("fetchTotalMsgs", "Error fetching total messages sent", e)
            }
        }
        return 0
    }

    suspend fun setTotalMsgs(newMsgs: Int) {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                userDocument.update("total_messages_sent", newMsgs).await()
                _totalMsgs.value = newMsgs
            } catch (e: FirebaseFirestoreException) {
                Log.e("setTotalMsgs", "Error setting total messages sent", e)
            }
        }
    }

    private suspend fun fetchTextToSpeech() {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                val textToSpeechStatus = userDocument.get().await()["text_to_speech"] as? Boolean
                if (textToSpeechStatus != null) {
                    _textToSpeech.value = textToSpeechStatus
                }
            } catch (e: FirebaseFirestoreException) {
                Log.e("fetchTextToSpeech", "Error fetching text-to-speech status", e)
            }
        }
    }

    suspend fun setTextToSpeech(newStatus: Boolean): Boolean {
        val userDocument = getUserDocument()
        return if (userDocument != null) {
            try {
                userDocument.update("text_to_speech", newStatus).await()
                _textToSpeech.value = newStatus
                true
            } catch (e: FirebaseFirestoreException) {
                Log.e("setTextToSpeech", "Error setting text-to-speech status", e)
                false
            }
        } else {
            false
        }
    }

    private suspend fun fetchDarkMode() {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                val darkModeStatus = userDocument.get().await()["dark_mode"] as? Boolean
                if (darkModeStatus != null) {
                    _darkMode.value = darkModeStatus
                }
            } catch (e: FirebaseFirestoreException) {
                Log.e("fetchDarkMode", "Error fetching dark mode status", e)
            }
        }
    }

    suspend fun setDarkMode(newStatus: Boolean): Boolean {
        val userDocument = getUserDocument()
        return if (userDocument != null) {
            try {
                userDocument.update("dark_mode", newStatus).await()
                _darkMode.value = newStatus
                true
            } catch (e: FirebaseFirestoreException) {
                Log.e("setDarkMode", "Error setting dark mode status", e)
                false
            }
        } else {
            false
        }
    }

    fun initUserDocumentAsync(user: FirebaseUser): Deferred<Boolean> {
        val userDocument = getUserDocument()
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
                        )
                        userDocument.set(defaultData, SetOptions.merge()).await() // Force sync
                        fetchData()
                    }
                    true
                } catch (e:Exception) {
                    Log.e("initUserDocumentAsync", "Error initializing user document", e)
                    false
                }
            }
        } else {
            return CoroutineScope(Dispatchers.IO).async { false }
        }
    }

    suspend fun deleteOldDailyLoginReward() {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                firestore.runTransaction { transaction ->
                    transaction.update(userDocument, "daily_login_day", "")
                }.await()
            } catch (e: FirebaseFirestoreException) {
                Log.e("deleteOldDailyLoginReward", "Error deleting old daily login reward", e)
            }
        }
    }

    suspend fun scheduleMonthlyCredits(newCredits: Int) {
        val userDocument = withContext(Dispatchers.IO) { getUserDocument() }
        if (userDocument != null) {
            val userData = userDocument.get().await()
            val subscriptionType = userData.get("subscription_status") as? String
            val currentCredits = userData.get("credits") as? Long
            val lastCreditDateStr = userData.get("last_credit_date") as? String

            if (subscriptionType == "LITE" && currentCredits != null) {
                if (lastCreditDateStr.isNullOrEmpty()) {
                    val lastCreditDate = LocalDateTime.now()
                    val nextCreditDate = lastCreditDate!!.plusHours(1)
                    Log.i("null lite credits lastcreditdate", lastCreditDateStr.toString())
                    Log.i("nextCreditDate from empty", nextCreditDate.toString())
                    userDocument.update("credits", currentCredits + newCredits,
                        "last_credit_date", nextCreditDate.toString()).await()
                } else {
                    Log.i("lastCreditDateStr parse", lastCreditDateStr.toString())
                    lastCreditDate = LocalDateTime.parse(lastCreditDateStr)
                    val nextCreditDate = lastCreditDate!!.plusMonths(1)
                    Log.i("lastcreditdate", lastCreditDate.toString())
                    Log.i("nextCreditDate", nextCreditDate.toString())
                    if (LocalDateTime.now().isAfter(lastCreditDate)) {
                        userDocument.update("credits", currentCredits + newCredits,
                            "last_credit_date", nextCreditDate.toString()).await()
                        Log.i("nextcreditdate updated", nextCreditDate.toString())
                    }
                }
            }
        }
    }

    suspend fun fetchDailyLoginReward() {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                val dailyLoginDayValue = userDocument.get().await()["daily_login_day"] as? String
                if (dailyLoginDayValue != null) {
                    _dailyLoginDay.value = dailyLoginDayValue
                }
            } catch (e: FirebaseFirestoreException) {
                Log.e("fetchDailyLoginReward", "Error fetching daily login reward", e)
            }
        }
    }

    suspend fun setDailyLoginDay(todayString: String) {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                userDocument.update("daily_login_day", todayString).await()
                _dailyLoginDay.value = todayString
            } catch (e: FirebaseFirestoreException) {
                Log.e("setDailyLoginDay", "Error setting daily login day", e)
            }
        }
    }
    private suspend fun updateUserActiveTimestamp() {
        val userDocument = getUserDocument() ?: return
        try {
            val serverTimestamp = FieldValue.serverTimestamp()
            userDocument.update(mapOf("last_active" to serverTimestamp,
                "last_notification_check" to serverTimestamp)).await()
        } catch (e: FirebaseFirestoreException) {
            Log.e("updateUserActiveTimestamp", "Error updating user active timestamp", e)
        }
    }

    suspend fun onUserActivity(context: Context) {
        try {
            updateUserActiveTimestamp()
            // Re-schedule inactivity notification whenever the user is active.
            NotificationScheduler.scheduleInactiveNotification(context)
        } catch (e: FirebaseFirestoreException) {
            Log.e("onUserActivity", "Error updating user activity", e)
        }
    }

    suspend fun saveFcmToken(token: String) {
        val userDocument = getUserDocument()
        if (userDocument != null) {
            try {
                userDocument.update("fcm_token", token).await()
            } catch (e: FirebaseFirestoreException) {
                Log.e("saveFcmToken", "Error saving FCM token", e)
            }
        }
    }
}
