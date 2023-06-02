package com.internaltest.sarahchatbotmvp.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthStateProvider(auth: FirebaseAuth) {

    private val _authState = MutableLiveData<FirebaseUser?>(auth.currentUser)
    val authState: LiveData<FirebaseUser?> get() = _authState

    private val _authError = MutableLiveData(false)
    val authError: LiveData<Boolean> get() = _authError

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser

            // Set authError to true when the user is not logged in
            _authError.value = firebaseAuth.currentUser == null
        }
    }
}
