package com.internaltest.sarahchatbotmvp.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.SignInButton
import com.internaltest.sarahchatbotmvp.MainActivity
import com.internaltest.sarahchatbotmvp.R
import com.qonversion.android.sdk.Qonversion
import java.util.*

class SignIn : AppCompatActivity() {
    private var signInButton: SignInButton? = null
    private var privacyBtn: Button? = null
    private var termsOfUseBtn: Button? = null
    private var mSignInClient: GoogleSignInClient? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        privacyBtn = findViewById(R.id.privacy)
        termsOfUseBtn = findViewById(R.id.terms_of_use)
        with(privacyBtn) {
            this?.setOnClickListener { gotoPrivacy() }
        }
        with(termsOfUseBtn) {
            this?.setOnClickListener { gotoTermsOfUse() }
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        mSignInClient = GoogleSignIn.getClient(this, gso)
        signInButton = findViewById(R.id.sign_in_button)
        with(signInButton) {
            this?.setOnClickListener {
                val intent = mSignInClient!!.signInIntent
                startActivityForResult(intent, RC_SIGN_IN)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(
                data!!
            )
            Objects.requireNonNull(result)?.let { handleSignInResult(it) }
        }
    }

    private fun handleSignInResult(result: GoogleSignInResult) {
        val task = mSignInClient!!.silentSignIn()
        if (result.isSuccess && task.isSuccessful) {
            val account = task.result
            Objects.requireNonNull(account.id)?.let { Qonversion.shared.identify(it) }
            gotoChat()
        } else {
            Toast.makeText(
                applicationContext,
                "Login cancelado/Erro durante Login",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun gotoChat() {
        val intent = Intent(this@SignIn, MainActivity::class.java)
        startActivity(intent)
    }

    private fun gotoPrivacy() {
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("PRIVACY_LINK_HERE")
        )
        startActivity(browserIntent)
    }

    private fun gotoTermsOfUse() {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("TERMS_OF_USE_LINK_HERE"))
        startActivity(browserIntent)
    }

    companion object {
        private const val RC_SIGN_IN = 1
    }
}