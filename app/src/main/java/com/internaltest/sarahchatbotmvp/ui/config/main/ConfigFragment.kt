package com.internaltest.sarahchatbotmvp.ui.config.main

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.auth.SignIn
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.data.SaveLoadConversationManager
import com.internaltest.sarahchatbotmvp.data.Utils
import com.internaltest.sarahchatbotmvp.data.Utils.imageProfile
import com.internaltest.sarahchatbotmvp.data.Utils.messageList
import com.internaltest.sarahchatbotmvp.data.Utils.msgs
import com.internaltest.sarahchatbotmvp.data.Utils.userEmail
import com.internaltest.sarahchatbotmvp.data.Utils.userId
import com.internaltest.sarahchatbotmvp.data.Utils.userName
import com.internaltest.sarahchatbotmvp.data.WalletRepo
import com.internaltest.sarahchatbotmvp.databinding.FragmentConfigBinding
import com.internaltest.sarahchatbotmvp.models.Conversation
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity
import com.internaltest.sarahchatbotmvp.ui.wallet.Wallet
import com.internaltest.sarahchatbotmvp.utils.DialogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale


class ConfigFragment() : Fragment() {
    private lateinit var binding: FragmentConfigBinding

    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreRepo: FirestoreRepo
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var conversationManager: SaveLoadConversationManager

    private var mSignInClient: GoogleSignInClient? = null

    private var isDarkModeOn: Boolean? = null
    private var initialFontSize: Int? = null
    private var textToSpeechInitialized = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        mSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        conversationManager = SaveLoadConversationManager(requireActivity(), requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestoreRepo = FirestoreRepo()
        setupInfo()
        toggleDarkMode()
        configTextSizeSeekbar()
        configTextToSpeech()

        binding.imgBtnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.llSaveButton.setOnClickListener {
            saveConversation()
        }

        binding.llLoadButton.setOnClickListener {
            loadConversation()
        }

        binding.llLogoutButton.setOnClickListener {
            logout()
        }

        binding.llDeleteAccountButton.setOnClickListener {
            deleteUserAccountAndData()
        }

        binding.cvCta.setOnClickListener {
            startActivity(Intent(requireContext(), Wallet::class.java))
        }

        binding.imgBtnHelp.setOnClickListener {
            openDialogHelp()
        }
    }

    private fun setupInfo() {
        binding.tvUserName.text = userName
        binding.tvUserEmail.text = userEmail
        try {
            imageProfile?.let {
                Glide.with(requireContext())
                    .load(it)
                    .into(binding.imgUser)
            }
        } catch (e: NullPointerException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Toast.makeText(requireContext(), "imagem não encontrada", Toast.LENGTH_LONG).show()
        }
    }

    private fun configTextSizeSeekbar() {
        CoroutineScope(Dispatchers.Main).launch {
            initialFontSize = firestoreRepo.fetchFontSize().first()
            binding.fontSizeSeekBar.progress = (initialFontSize ?: 20) - 10
        }
        binding.fontSizeSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
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

    private fun toggleDarkMode() {
        CoroutineScope(Dispatchers.Main).launch {
            firestoreRepo.darkMode.first { darkMode ->
                binding.toggleDarkSwitch.isChecked = darkMode
                isDarkModeOn = darkMode
                true
            }
        }

        binding.toggleDarkSwitch.setOnClickListener {
            val isChecked = binding.toggleDarkSwitch.isChecked
            if (isChecked != isDarkModeOn) {
                isDarkModeOn = isChecked
                CoroutineScope(Dispatchers.Main).launch {
                    firestoreRepo.setDarkMode(isChecked)
                }
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
        }
    }

    private fun configTextToSpeech() {

        observeTextToSpeech()

        binding.toggleAudioSwitch.apply {
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
    }

    private fun observeTextToSpeech() {
        firestoreRepo.textToSpeech.asLiveData().observe(viewLifecycleOwner) { textToSpeechEnabled ->
            binding.toggleAudioSwitch.isChecked = textToSpeechEnabled
//            if (textToSpeechEnabled && mainActivity.textToSpeechInitialized) {
//                mainActivity.textToSpeech.language = Locale("pt", "BR")
//                lifecycleScope.launch {
//                    mainActivity.speakText("Texto para Fala Ativado")
//                }
//            }
        }
    }

    private fun openDialogHelp() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_help)
        //quero que a dialog tenha o maximo de largura possivel e margens de 16dp
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.decorView?.setPadding(16, 0, 16, 0)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))


        val btnClose = dialog.findViewById<View>(R.id.close)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        val btnClearCache = dialog.findViewById<View>(R.id.cv_btn_clear_cache)
        btnClearCache.setOnClickListener {
            clearCache()
        }

        val btnFaleConosco = dialog.findViewById<View>(R.id.cv_btn_contact)
        btnFaleConosco.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "message/rfc822"
            intent.putExtra(
                Intent.EXTRA_EMAIL, arrayOf("teqbot.io59@gmail.com")
            )
            intent.putExtra(Intent.EXTRA_SUBJECT, "Fale Conosco")

            startActivity(Intent.createChooser(intent, "Escolha o aplicativo de email"))
        }

        val btnReportConversation = dialog.findViewById<View>(R.id.tv_btn_report)
        btnReportConversation.setOnClickListener {
            reportMessageDialog()
        }

        dialog.show()
    }

    private fun reportMessageDialog() {
        DialogUtils.showDialogReportChat(
            context = requireContext(),
            onSuccess = { reportMessageSendToFirebase() })
    }

    private fun reportMessageSendToFirebase() {
        val conversation = Conversation(userName.toString(), messageList, userId.toString())
        DialogUtils.showDialogConfirmReportChat(context = requireContext(), onSuccess = {
            conversationManager.saveReportConversation(conversation)
        })
    }

    private fun clearCache() {
        val result = clearCacheFunction(requireActivity().applicationContext)
        if (result) {
            Toast.makeText(
                requireActivity().applicationContext,
                "Cache limpo com sucesso",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                requireActivity().applicationContext,
                "Falha ao tentar limpar cache.",
                Toast.LENGTH_SHORT
            )
                .show()
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

    private fun saveConversation() {
        userName?.let { userName ->
            userId?.let { userId ->
                conversationManager.saveConversationOnClick(
                    userName,
                    userId
                )
            }
        }
    }

    private fun loadConversation() {
        DialogUtils.showLoadConversationDialog(
            requireContext(), conversationManager,
            conversationManager::loadConversationOnClick
        )
    }

    private fun logout() {
        DialogUtils.showLogoutAlertDialog(requireContext()) {
            mSignInClient?.signOut()?.addOnCompleteListener(requireActivity()) {
                msgs.clear()
                messageList.clear()
                goToSignIn()
            }
        }
    }

    private fun deleteUserAccountAndData() {
        DialogUtils.showDeleteAccountConfirmationDialog(
            requireContext(),
            conversationManager,
            auth
        ) {
            goToSignIn()
        }
    }

    private fun goToSignIn() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(requireContext(), SignIn::class.java)
        requireActivity().startActivity(intent)
    }
}