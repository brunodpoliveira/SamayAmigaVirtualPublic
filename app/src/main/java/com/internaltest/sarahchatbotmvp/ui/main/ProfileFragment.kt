package com.internaltest.sarahchatbotmvp.ui.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.auth.SignIn
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.data.SaveLoadConversationManager
import com.internaltest.sarahchatbotmvp.ui.wallet.Wallet
import com.internaltest.sarahchatbotmvp.utils.DialogUtils.showDeleteAccountConfirmationDialog
import com.internaltest.sarahchatbotmvp.utils.DialogUtils.showLoadConversationDialog
import com.internaltest.sarahchatbotmvp.utils.DialogUtils.showLogoutAlertDialog
import com.internaltest.sarahchatbotmvp.utils.DialogUtils.showToggleDarkModeAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreRepo: FirestoreRepo
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var conversationManager: SaveLoadConversationManager
    private val mainActivity by lazy { activity as MainActivity }

    private var logoutBtn: Button? = null
    private var walletBtn: Button? = null
    private var deleteAccountBtn: Button? = null
    private var userName: TextView? = null
    private var userEmail: TextView? = null
    private var userId: TextView? = null
    private var firebaseId: TextView? = null
    private var profileImage: ImageView? = null
    private var mSignInClient: GoogleSignInClient? = null
    private var toggleDarkSwitch: SwitchCompat? = null
    private var textToSpeechSwitch: SwitchCompat? = null
    private var isDarkModeOn: Boolean? = null
    private var isTextToSpeechOn: Boolean? = null
    private var textToSpeechInitialized = false
    private var fontSizeSeekBar: SeekBar? = null
    private var initialFontSize: Int? = null
    private var saveConversationButton: Button? = null
    private var loadConversationButton: Button? = null

    companion object {
        private const val REQUEST_CODE_PICK_JSON_FILE = 100
        private const val REQUEST_CODE_PERMISSIONS = 200
        const val OPEN_DOCUMENT_REQUEST_CODE = 300
        const val OPEN_LOCAL_FILE_REQUEST_CODE = 400
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestoreRepo = FirestoreRepo()
        logoutBtn = view.findViewById(R.id.logoutBtn)
        walletBtn = view.findViewById(R.id.walletBtn)
        deleteAccountBtn = view.findViewById(R.id.deleteAccountBtn)
        userName = view.findViewById(R.id.name)
        userEmail = view.findViewById(R.id.email)
        userId = view.findViewById(R.id.userId)
        firebaseId = view.findViewById(R.id.firebaseId)
        profileImage = view.findViewById(R.id.profileImage)
        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        mSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        with(logoutBtn) { this?.setOnClickListener { logout() } }
        with(walletBtn) {
            this?.setOnClickListener { goToWallet() }
        }
        with(deleteAccountBtn) {
            this?.setOnClickListener { deleteUserAccountAndData() }
        }

        conversationManager = SaveLoadConversationManager(mainActivity, requireContext())

        saveConversationButton = view.findViewById(R.id.saveConversationButton)
        saveConversationButton?.setOnClickListener {
            conversationManager.saveConversationOnClick(
                userName?.text.toString(),
                userId = userId?.text.toString()
            )
        }

        loadConversationButton = view.findViewById(R.id.loadConversationButton)
        loadConversationButton?.setOnClickListener {
            showLoadConversationDialog(requireContext(), conversationManager,
                conversationManager::loadConversationOnClick)
        }

        parentFragment?.view?.findViewById<NavigationView>(R.id.navigation_view)?.apply {
            val topPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 144f, resources.displayMetrics).toInt()
            setPadding(paddingLeft, topPadding, paddingRight, paddingBottom)
        }

        toggleDarkSwitch = view.findViewById(R.id.toggleDarkSwitch)
        CoroutineScope(Dispatchers.Main).launch {
            isDarkModeOn = firestoreRepo.darkMode.first()
            toggleDarkSwitch?.isChecked = isDarkModeOn as Boolean
        }

        CoroutineScope(Dispatchers.Main).launch {
            isTextToSpeechOn = firestoreRepo.textToSpeech.first()
            textToSpeechSwitch?.isChecked = isTextToSpeechOn as Boolean
        }

        textToSpeechSwitch = view.findViewById<SwitchCompat>(R.id.textToSpeechSwitch).apply {
            setOnCheckedChangeListener { _, isChecked ->
                CoroutineScope(Dispatchers.Main).launch {
                    firestoreRepo.setTextToSpeech(isChecked)
                }
            }
        }
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale("pt", "BR")
                textToSpeechInitialized = true
            }
        }
        observeTextToSpeech()

        //implementando lógica do botão modo escuro
        toggleDarkSwitch?.setOnClickListener {
            val isChecked = toggleDarkSwitch?.isChecked ?: false
            showToggleDarkModeAlertDialog(
                context = requireContext(),
                isChecked = isChecked,
                toggleDarkSwitch = toggleDarkSwitch!!,
                applyDarkMode = { newValue ->
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.setDarkMode(newValue)
                        Log.i("new dark mode value", newValue.toString())
                    }
                },
                newDarkModeValue = { !(isDarkModeOn ?: false) }
            )
        }
        fontSizeSeekBar = view.findViewById(R.id.fontSizeSeekBar)
        CoroutineScope(Dispatchers.Main).launch {
            initialFontSize = firestoreRepo.fetchFontSize().first()
            fontSizeSeekBar?.progress = (initialFontSize ?: 20) - 10
        }
        fontSizeSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newFontSize = progress + 10 // Here we consider that SeekBar minimum is 10
                if (fromUser && initialFontSize != null && newFontSize != initialFontSize) {
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.setFontSize(newFontSize)
                    }
                    initialFontSize = newFontSize
                }
            }
        })
    }

    private fun observeTextToSpeech() {
        firestoreRepo.textToSpeech.asLiveData().observe(viewLifecycleOwner) { textToSpeechEnabled ->
            textToSpeechSwitch?.isChecked = textToSpeechEnabled
            if (textToSpeechEnabled && mainActivity.textToSpeechInitialized) {
                mainActivity.textToSpeech.language = Locale("pt", "BR")
                lifecycleScope.launch {
                    mainActivity.speakText("Texto para Fala Ativado")
                }
            }
        }
    }

    private fun logout() {
        showLogoutAlertDialog(requireContext()) {
            mSignInClient?.signOut()?.addOnCompleteListener(requireActivity()) {

                goToSignIn()
            }
        }
    }

    private fun deleteUserAccountAndData() {
        showDeleteAccountConfirmationDialog(requireContext(), conversationManager,auth) {
            goToSignIn()
        }
    }

    private fun goToWallet() {
        val intent = Intent(requireContext(), Wallet::class.java)
        requireActivity().startActivity(intent)
    }

    private fun goToSignIn() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(requireContext(), SignIn::class.java)
        requireActivity().startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        val task = mSignInClient!!.silentSignIn()
        if (task.isSuccessful) {
            val account = task.result
            if (account != null) {
                userName?.text = account.displayName
            }
            if (account != null) {
                userEmail?.text = account.email
            }
            if (account != null) {
                userId?.text = account.id
            }
            if (account != null){
                firebaseId?.text = auth.currentUser?.uid
            }
            try {
                if (account != null) {
                    Glide.with(this).load(account.photoUrl).into(profileImage!!)
                }
            } catch (e: NullPointerException) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Toast.makeText(requireContext(), "imagem não encontrada", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(requireContext(), "erro no login", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("update")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                OPEN_DOCUMENT_REQUEST_CODE, OPEN_LOCAL_FILE_REQUEST_CODE -> {
                    data.data?.let {
                        conversationManager.loadConversation(requestCode == OPEN_DOCUMENT_REQUEST_CODE)
                    }
                }
            }
        }
    }

    @Deprecated("update")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All required permissions are granted, retry the file picker intent
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent, REQUEST_CODE_PICK_JSON_FILE)
            } else {
                Toast.makeText(requireContext(), "Permissão negada",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        view?.visibility = if (hidden) View.GONE else View.VISIBLE
    }
}