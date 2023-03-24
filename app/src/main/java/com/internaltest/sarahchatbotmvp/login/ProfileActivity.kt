package com.internaltest.sarahchatbotmvp.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.internaltest.sarahchatbotmvp.MainActivity
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.wallet.Wallet

//TODO integrar modo escuro
class ProfileActivity : AppCompatActivity() {
    private var logoutBtn: Button? = null
    private var goToChat: Button? = null
    private var walletBtn: Button? = null
    private var userName: TextView? = null
    private var userEmail: TextView? = null
    private var userId: TextView? = null
    private var profileImage: ImageView? = null
    private var mSignInClient: GoogleSignInClient? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        logoutBtn = findViewById(R.id.logoutBtn)
        goToChat = findViewById(R.id.goToChat)
        walletBtn = findViewById(R.id.walletBtn)
        userName = findViewById(R.id.name)
        userEmail = findViewById(R.id.email)
        userId = findViewById(R.id.userId)
        profileImage = findViewById(R.id.profileImage)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        mSignInClient = GoogleSignIn.getClient(this, gso)
        with(logoutBtn) { this?.setOnClickListener { logout() } }
        with(goToChat) {
            this?.setOnClickListener { gotoMainActivity() }
        }
        with(walletBtn) {
            this?.setOnClickListener { goToWallet() }
        }
    }

    private fun logout() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setMessage("Tem certeza? Todos os dados serão perdidos, " +
                "incluindo sua assinatura e créditos")
        alertDialog.setPositiveButton("Logout") { _, _ ->
            mSignInClient!!.signOut().addOnCompleteListener(this) { goToSignIn() }
        }
        alertDialog.setNegativeButton("Voltar") { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }

    override fun onStart() {
        super.onStart()
        val task = mSignInClient!!.silentSignIn()
        if (task.isSuccessful) {
            val account = task.result
            if (account != null) {
                userName!!.text = account.displayName
            }
            if (account != null) {
                userEmail!!.text = account.email
            }
            if (account != null) {
                userId!!.text = account.id
            }
            try {
                if (account != null) {
                    Glide.with(this).load(account.photoUrl).into(profileImage!!)
                }
            } catch (e: NullPointerException) {
                Toast.makeText(applicationContext, "image not found", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(applicationContext, "login error", Toast.LENGTH_LONG).show()
        }
    }

    //apertar o retornar quebra a contagem de créditos, então desativar é a melhor escolha
    override fun onBackPressed() {
        Toast.makeText(
            applicationContext,
            "Botão voltar desligado. Use as opções do menu", Toast.LENGTH_LONG
        ).show()
    }

    private fun gotoMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun goToWallet() {
        val intent = Intent(this, Wallet::class.java)
        startActivity(intent)
    }

    private fun goToSignIn() {
        val intent = Intent(this, SignIn::class.java)
        startActivity(intent)
    }
}