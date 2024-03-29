package com.internaltest.sarahchatbotmvp.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.internaltest.sarahchatbotmvp.BuildConfig
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.databinding.ActivitySignInBinding
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import com.qonversion.android.sdk.Qonversion
import kotlinx.coroutines.launch
import java.util.Objects

class SignIn : AppCompatActivity() {
    private lateinit var binding: ActivitySignInBinding
    private var mSignInClient: GoogleSignInClient? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreRepo: FirestoreRepo

    companion object {
        private const val RC_SIGN_IN = 1
        var isCurrentActivity = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        // If user is already logged in, redirect to MainActivity
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            gotoChat()
            finish()
            return
        }
        setContentView(binding.root)

        auth = Firebase.auth
        firestoreRepo = FirestoreRepo()

        val signInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val response = Auth.GoogleSignInApi.getSignInResultFromIntent(
                        result.data!!)
                    Objects.requireNonNull(response)?.let { handleSignInResult(it) }
                }
                if (result.resultCode == Activity.RESULT_CANCELED) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.cvBtnLogin.visibility = android.view.View.VISIBLE
                    Toast.makeText(
                        applicationContext,
                        "Login cancelado/Erro durante Login",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        binding.tvBtnPrivacy.setOnClickListener {
            gotoPrivacy()
        }

        binding.tvBtnTermsUse.setOnClickListener {
            gotoTermsOfUse()
        }

        binding.cvBtnLogin.setOnClickListener {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.cvBtnLogin.visibility = android.view.View.INVISIBLE
            val intent = mSignInClient!!.signInIntent
            signInLauncher.launch(intent)
        }


        initCarousel()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_SIGN_IN_REQUEST_ID_TOKEN)
            .requestEmail()
            .build()
        mSignInClient = GoogleSignIn.getClient(this, gso)

    }


    private fun initCarousel() {
        binding.carouselView.pageCount = 3
        binding.carouselView.setImageListener { position, imageView ->
            when (position) {
                0 -> imageView.setImageResource(R.drawable.carrossel1)
                1 -> imageView.setImageResource(R.drawable.carrossel2)
                2 -> imageView.setImageResource(R.drawable.carrossel3)
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


////    private fun clearCache() {
////        val result = clearCacheFunction(applicationContext)
////        if (result) {
////            Toast.makeText(applicationContext, "Cache limpo com sucesso", Toast.LENGTH_SHORT).show()
////        } else {
////            Toast.makeText(applicationContext, "Falha ao tentar limpar cache.", Toast.LENGTH_SHORT)
////                .show()
////        }
////    }
//
//    private fun clearCacheFunction(context: Context): Boolean {
//        return try {
//            val cacheDir = context.cacheDir
//            deleteFile(cacheDir)
//            true
//        } catch (e: Exception) {
//            FirebaseCrashlytics.getInstance().recordException(e)
//            e.printStackTrace()
//            false
//        }
//    }

//    private fun deleteFile(file: File) {
//        if (file.isDirectory) {
//            val children = file.list()
//            for (child in children!!) {
//                deleteFile(File(file, child))
//            }
//        } else {
//            file.delete()
//        }
//    }

    private fun handleSignInResult(result: GoogleSignInResult) {
        val task = mSignInClient!!.silentSignIn()
        if (result.isSuccess && task.isSuccessful) {
            val account = task.result
            Objects.requireNonNull(account.id)?.let { Qonversion.shared.identify(it) }
            val idToken = account?.idToken
            if (idToken != null) {
                firebaseAuthWithGoogle(idToken)
            } else {
                binding.progressBar.visibility = android.view.View.GONE
                binding.cvBtnLogin.visibility = android.view.View.VISIBLE
                Log.e(TAG, "Error: idToken is null")
                Toast.makeText(
                    applicationContext,
                    "Login cancelado/Erro durante Login: idToken is null",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            binding.progressBar.visibility = android.view.View.GONE
            binding.cvBtnLogin.visibility = android.view.View.VISIBLE
            Log.e(
                TAG, "Login failed. Status code: ${result.status.statusCode}. " +
                        "Status message: ${result.status.statusMessage}"
            )
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
                        val isDocumentCreatedOrExists =
                            firestoreRepo.initUserDocumentAsync(user!!).await()
                        if (!isDocumentCreatedOrExists) {
                            binding.progressBar.visibility = android.view.View.GONE
                            binding.cvBtnLogin.visibility = android.view.View.VISIBLE
                            Log.w(TAG, "initUserDocumentAsync:failure", task.exception)
                            Toast.makeText(
                                applicationContext,
                                "Login cancelado/Erro durante Login initUserDocumentAsync",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            firestoreRepo.onUserActivity(applicationContext)
                            gotoChat()
                        }
                    }
                } else {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.cvBtnLogin.visibility = android.view.View.VISIBLE
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(
                        applicationContext, "Login cancelado/Erro durante Login",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun gotoChat() {
        val intent = Intent(this@SignIn, MainActivity::class.java)
        startActivity(intent)
    }

    private fun gotoPrivacy() {
        val browserIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://teqbot.com.br/notificacao-de-privacidade/")
            )
        if (browserIntent.resolveActivity(packageManager) != null) {
            startActivity(browserIntent)
        } else {
            Toast.makeText(
                this, "Navegador de internet não encontrado. " +
                        "Por favor, instale um navegador.", Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun gotoTermsOfUse() {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://teqbot.com.br/termos-de-servico/"))
        if (browserIntent.resolveActivity(packageManager) != null) {
            startActivity(browserIntent)
        } else {
            Toast.makeText(
                this, "Navegador de internet não encontrado. " +
                        "Por favor, instale um navegador.", Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        ActivityCompat.finishAffinity(this)
    }
}