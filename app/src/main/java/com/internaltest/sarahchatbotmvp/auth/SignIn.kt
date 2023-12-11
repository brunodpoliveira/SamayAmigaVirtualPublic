package com.internaltest.sarahchatbotmvp.auth

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.internaltest.sarahchatbotmvp.BuildConfig
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import com.qonversion.android.sdk.Qonversion
import kotlinx.coroutines.launch
import java.io.File
import java.util.Objects

class SignIn : AppCompatActivity() {
    private var signInButton: SignInButton? = null
    private var clearCacheBtn: Button? = null
    private var privacyBtn: Button? = null
    private var termsOfUseBtn: Button? = null
    private var mSignInClient: GoogleSignInClient? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreRepo: FirestoreRepo

    companion object {
        private const val RC_SIGN_IN = 1
        var isCurrentActivity = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If user is already logged in, redirect to MainActivity
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            gotoChat()
            finish()
            return
        }
        setContentView(R.layout.activity_sign_in)
        privacyBtn = findViewById(R.id.privacy)
        termsOfUseBtn = findViewById(R.id.terms_of_use)
        clearCacheBtn = findViewById(R.id.clear_cache)
        auth = Firebase.auth
        firestoreRepo = FirestoreRepo()
        with(privacyBtn) {
            this?.setOnClickListener { gotoPrivacy() }
        }
        with(termsOfUseBtn) {
            this?.setOnClickListener { gotoTermsOfUse() }
        }
        with(clearCacheBtn) {
            this?.setOnClickListener { clearCache() }
            this?.setOnLongClickListener{
                val tooltipText = "Apertando esse botão irá limpar o cache do app, " +
                        "potencialmente resolvendo quaisquer problemas que você esteja enfrentando"
                val duration = Toast.LENGTH_LONG
                Toast.makeText(applicationContext, tooltipText, duration).show()
                true
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_SIGN_IN_REQUEST_ID_TOKEN)
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
    override fun onResume() {
        super.onResume()
        isCurrentActivity = true
    }
    override fun onPause() {
        super.onPause()
        isCurrentActivity = false
    }

    @Deprecated("update")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(
                data!!
            )
            Objects.requireNonNull(result)?.let { handleSignInResult(it) }
        }
    }

    private fun clearCache() {
        val result = clearCacheFunction(applicationContext)
        if (result) {
            Toast.makeText(applicationContext, "Cache limpo com sucesso", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(applicationContext, "Falha ao tentar limpar cache.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCacheFunction(context: Context): Boolean {
        return try {
            val cacheDir = context.cacheDir
            deleteFile(cacheDir)
            true
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            e.printStackTrace()
            false
        }
    }

    private fun deleteFile(file: File) {
        if (file.isDirectory) {
            val children = file.list()
            for (child in children!!) {
                deleteFile(File(file, child))
            }
        } else {
            file.delete()
        }
    }

    private fun handleSignInResult(result: GoogleSignInResult) {
        val task = mSignInClient!!.silentSignIn()
        if (result.isSuccess && task.isSuccessful) {
            val account = task.result
            Objects.requireNonNull(account.id)?.let { Qonversion.shared.identify(it) }
            val idToken = account?.idToken
            if (idToken != null) {
                firebaseAuthWithGoogle(idToken)
            } else {
                Log.e(TAG, "Error: idToken is null")
                Toast.makeText(
                    applicationContext,
                    "Login cancelado/Erro durante Login: idToken is null",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Log.e(TAG, "Login failed. Status code: ${result.status.statusCode}. " +
                    "Status message: ${result.status.statusMessage}")
            // Display the error message in a Toast
            Toast.makeText(
                applicationContext,
                "Login failed. Status code: ${result.status.statusCode}. " +
                        "Status message: ${result.status.statusMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    lifecycleScope.launch {
                        val isDocumentCreatedOrExists = firestoreRepo.initUserDocumentAsync(user!!).await()
                        if (!isDocumentCreatedOrExists) {
                            Log.w(TAG, "initUserDocumentAsync:failure", task.exception)
                            Toast.makeText(applicationContext,
                                "Login cancelado/Erro durante Login initUserDocumentAsync",
                                Toast.LENGTH_LONG).show()
                        } else {
                            firestoreRepo.onUserActivity(applicationContext)
                            gotoChat()
                        }
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(applicationContext, "Login cancelado/Erro durante Login",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun gotoChat() {
        val intent = Intent(this@SignIn, MainActivity::class.java)
        startActivity(intent)
    }

    private fun gotoPrivacy() {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://teqbot.com.br/notificacao-de-privacidade/"))
        if (browserIntent.resolveActivity(packageManager) != null) {
            startActivity(browserIntent)
        } else {
            Toast.makeText(this, "Navegador de internet não encontrado. " +
                    "Por favor, instale um navegador.", Toast.LENGTH_LONG).show()
        }
    }

    private fun gotoTermsOfUse() {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://teqbot.com.br/termos-de-servico/"))
        if (browserIntent.resolveActivity(packageManager) != null) {
            startActivity(browserIntent)
        } else {
            Toast.makeText(this, "Navegador de internet não encontrado. " +
                    "Por favor, instale um navegador.", Toast.LENGTH_LONG).show()
        }
    }
}