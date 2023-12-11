package com.internaltest.sarahchatbotmvp.auth

import com.google.firebase.auth.FirebaseAuth
import com.internaltest.sarahchatbotmvp.base.AuthStateListener

class AuthStateProvider(auth: FirebaseAuth) {

    private val authListeners: MutableList<AuthStateListener> = mutableListOf()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            for (listener in authListeners) {
                listener.onAuthStateChanged(firebaseAuth.currentUser)
            }
        }
    }

    fun addAuthStateListener(listener: AuthStateListener) {
        authListeners.add(listener)
    }

    fun removeAuthStateListener(listener: AuthStateListener) {
        authListeners.remove(listener)
    }
}