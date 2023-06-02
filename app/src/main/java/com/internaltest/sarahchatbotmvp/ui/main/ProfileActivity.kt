package com.internaltest.sarahchatbotmvp.ui.main

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.auth.SignIn
import com.internaltest.sarahchatbotmvp.ui.wallet.Wallet

class ProfileActivity : BaseActivity() {
    private var logoutBtn: Button? = null
    private var goToChat: Button? = null
    private var walletBtn: Button? = null
    private var deleteAccountBtn: Button? = null
    private var userName: TextView? = null
    private var userEmail: TextView? = null
    private var userId: TextView? = null
    private var firebaseId: TextView? = null
    private var profileImage: ImageView? = null
    private var mSignInClient: GoogleSignInClient? = null
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        logoutBtn = findViewById(R.id.logoutBtn)
        goToChat = findViewById(R.id.goToChat)
        walletBtn = findViewById(R.id.walletBtn)
        deleteAccountBtn = findViewById(R.id.deleteAccountBtn)
        userName = findViewById(R.id.name)
        userEmail = findViewById(R.id.email)
        userId = findViewById(R.id.userId)
        firebaseId = findViewById(R.id.firebaseId)
        profileImage = findViewById(R.id.profileImage)
        auth = FirebaseAuth.getInstance()
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
        with(deleteAccountBtn) {
            this?.setOnClickListener { deleteUserAccountAndData() }
        }

    }

    private fun logout() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setMessage("Tem certeza?")

        // Set the positive button with the default text color
        alertDialogBuilder.setPositiveButton("Logout", null)
        // Set the negative button with the default text color
        alertDialogBuilder.setNegativeButton("Voltar", null)

        val alertDialog = alertDialogBuilder.create()
        alertDialog.setOnShowListener {
            // Get the current theme
            val currentTheme = AppCompatDelegate.getDefaultNightMode()

            // Set the text color for the positive and negative buttons based on the current theme
            val buttonTextColor = if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                Color.WHITE
            } else {
                Color.BLACK
            }
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonTextColor)
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonTextColor)

            // Set the click listeners for the buttons after setting the text color
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                mSignInClient?.signOut()?.addOnCompleteListener(this) { goToSignIn() }

                alertDialog.dismiss() // Close the dialog after handling the click
            }

            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                alertDialog.dismiss() // Close the dialog after handling the click
            }
        }

        alertDialog.show()
    }
    private fun deleteUserAccountAndData() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setMessage("AVISO: Você tem certeza que deseja deletar sua conta e dados? " +
                "Esta ação não pode ser desfeita ")

        // Set the positive button with the default text color
        alertDialogBuilder.setPositiveButton("Deletar", null)
        // Set the negative button with the default text color
        alertDialogBuilder.setNegativeButton("Cancelar", null)

        val alertDialog = alertDialogBuilder.create()
        alertDialog.setOnShowListener {
            // Get the current theme
            val currentTheme = AppCompatDelegate.getDefaultNightMode()

            // Set the text color for the positive and negative buttons based on the current theme
            val buttonTextColor = if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                Color.WHITE
            } else {
                Color.BLACK
            }
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonTextColor)
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonTextColor)

            // Set the click listeners for the buttons after setting the text color
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val user = auth.currentUser
                if (user != null) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users")
                        .document(user.uid)
                        .delete()
                        .addOnSuccessListener {
                            // Delete user's account from FirebaseAuth
                            user.delete()
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(this, "Deletado com sucesso"
                                            ,Toast.LENGTH_LONG).show()
                                        goToSignIn()
                                    } else {
                                        Toast.makeText(this, "Falha durante processo." +
                                                "Tente novamente.", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Falha durante processo." +
                                    "Tente novamente.", Toast.LENGTH_LONG).show()
                        }
                }

                alertDialog.dismiss() // Close the dialog after handling the click
            }

            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                alertDialog.dismiss() // Close the dialog after handling the click
            }
        }

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
            if (account != null){
                firebaseId!!.text = auth.currentUser!!.uid
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