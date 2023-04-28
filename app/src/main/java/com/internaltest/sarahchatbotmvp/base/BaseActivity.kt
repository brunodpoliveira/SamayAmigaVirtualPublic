package com.internaltest.sarahchatbotmvp.base

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.internaltest.sarahchatbotmvp.Splash
import com.internaltest.sarahchatbotmvp.auth.AuthStateProvider
import com.internaltest.sarahchatbotmvp.auth.SignIn

open class BaseActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var authStateProvider: AuthStateProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        authStateProvider = AuthStateProvider(auth)

        observeAuthError()
    }

    private fun observeAuthError() {
        authStateProvider.authError.observe(this) { error ->
            if (error && !SignIn.isCurrentActivity && !Splash.isCurrentActivity) {
                showErrorDialogAndNavigateToLogin()
            }
        }
    }

    private fun showErrorDialogAndNavigateToLogin() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Sessão Expirada")
            .setMessage("Sua sessão expirou. Você será redirecionado a tela de login. " +
                    "Seus dados estão salvos, com exceção do histórico de conversa")
            .setPositiveButton("OK", null)
            .setCancelable(false)

        val alertDialog = alertDialogBuilder.create()
        alertDialog.setOnShowListener {
            // Get the current theme
            val currentTheme = AppCompatDelegate.getDefaultNightMode()

            // Set the text color for the positive button based on the current theme
            val buttonTextColor = if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                Color.WHITE
            } else {
                Color.BLACK
            }
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonTextColor)

            // Set the click listener for the button after setting the text color
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                gotoSignIn()

                alertDialog.dismiss() // Close the dialog after handling the click
            }
        }

        alertDialog.show()
    }

    private fun gotoSignIn() {
        val intent = Intent(this, SignIn::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
