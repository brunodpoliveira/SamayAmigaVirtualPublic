package com.internaltest.sarahchatbotmvp.base

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.internaltest.sarahchatbotmvp.auth.AuthStateProvider
import com.internaltest.sarahchatbotmvp.auth.SignIn
import com.internaltest.sarahchatbotmvp.data.WalletRepo
import com.internaltest.sarahchatbotmvp.utils.DialogUtils.showErrorDialogAndNavigateToLogin

interface AuthStateListener {
    fun onAuthStateChanged(user: FirebaseUser?)
}

open class BaseActivity : AppCompatActivity(), AuthStateListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var authStateProvider: AuthStateProvider
    lateinit var walletRepo: WalletRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        authStateProvider = AuthStateProvider(auth)
        authStateProvider.addAuthStateListener(this)
        walletRepo = WalletRepo()
    }

    private fun gotoSignIn() {
        val intent = Intent(this, SignIn::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        authStateProvider.removeAuthStateListener(this)
    }

    override fun onAuthStateChanged(user: FirebaseUser?) {
        if (user == null) {
            showErrorDialogAndNavigateToLogin(this) { gotoSignIn() }
        }
    }
}
