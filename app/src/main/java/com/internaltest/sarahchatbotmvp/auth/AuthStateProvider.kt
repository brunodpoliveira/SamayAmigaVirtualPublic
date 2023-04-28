package com.internaltest.sarahchatbotmvp.auth

import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.internaltest.sarahchatbotmvp.Splash

class AuthStateProvider(auth: FirebaseAuth) {

    private val _authState = MutableLiveData<FirebaseUser?>(auth.currentUser)

    val authError = MutableLiveData(false)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser

            // Set authError to true when the user is not logged in
            if (firebaseAuth.currentUser == null && !SignIn.isCurrentActivity
                && !Splash.isCurrentActivity) {
                authError.value = true
            }
        }
    }
}
