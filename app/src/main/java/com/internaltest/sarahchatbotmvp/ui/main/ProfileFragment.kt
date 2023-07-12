package com.internaltest.sarahchatbotmvp.ui.main

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.auth.SignIn
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.models.Conversation
import com.internaltest.sarahchatbotmvp.ui.wallet.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreRepo: FirestoreRepo
    private lateinit var textToSpeech: TextToSpeech

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

        saveConversationButton = view.findViewById(R.id.saveConversationButton)
        saveConversationButton?.setOnClickListener {
            (activity as MainActivity).let { MainActivity ->

                val subscription = MainActivity.subscriptionTextView?.text

                if (subscription == "PREMIUM" || subscription == "GPT4") {
                    val conversation = Conversation(
                        userName.toString(),
                        MainActivity.messageList
                    )
                    MainActivity.showProgressDialog("Salvando...")
                    val userNameText = userName?.text?.toString()
                    if (userNameText != null) {
                        saveConversation("$userNameText-${System.currentTimeMillis()}.json", conversation)
                    }
                    MainActivity.dismissProgressDialog()
                    MainActivity.showProgressDialog("Salvo!")
                    Handler(Looper.getMainLooper()).postDelayed({
                        MainActivity.dismissProgressDialog()
                    }, 2000) // This represents a delay of 2 seconds.

                } else {
                    Toast.makeText(requireContext(), "Disponível apenas para inscritos GPT4 ou Premium", Toast.LENGTH_LONG).show()
                }
            }
        }

        loadConversationButton = view.findViewById(R.id.loadConversationButton)
        loadConversationButton?.setOnClickListener {
            val files: Array<String> = requireContext().fileList()
            val jsonFiles: List<String> = files.filter { it.endsWith(".json") }

            if(jsonFiles.isEmpty()){
                Toast.makeText(context, "Nenhuma coversa encontrada", Toast.LENGTH_LONG).show()
            } else {
                val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                builder.setTitle("Escolha uma conversa")

                builder.setItems(jsonFiles.toTypedArray()) { dialog, which ->
                    openAndLoadConversation(jsonFiles[which])
                    dialog.dismiss()
                }
                builder.show()
            }
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

        //implementando lógica do botão modo escuro
        toggleDarkSwitch?.setOnClickListener {
            val isChecked = toggleDarkSwitch?.isChecked ?: false
            toggleDarkSwitch!!.isChecked = !isChecked // Revert the switch state temporarily
            val alertDialogBuilder = AlertDialog.Builder(requireContext())
            alertDialogBuilder.setMessage(
                "Tem certeza? Todo o histórico de conversa será apagado ao trocar tema"
            )
            // Set the positive button with the default text color
            alertDialogBuilder.setPositiveButton("Sim", null)
            // Set the negative button with the default text color
            alertDialogBuilder.setNegativeButton("Não", null)

            val alertDialog = alertDialogBuilder.create()
            alertDialog.setOnShowListener {
                // Get the current theme
                val currentTheme = AppCompatDelegate.getDefaultNightMode()

                // Set the text color for the positive and negative buttons based on the current theme
                val buttonTextColor =
                    if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setTextColor(buttonTextColor)
                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setTextColor(buttonTextColor)

                // Set the click listeners for the buttons after setting the text color
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    // Toggle the dark mode value and update it in Firestore

                    val newDarkModeValue = !(isDarkModeOn ?: false)
                    isDarkModeOn = newDarkModeValue
                    CoroutineScope(Dispatchers.Main).launch {
                        firestoreRepo.setDarkMode(newDarkModeValue)
                        Log.i("new dark mode value", newDarkModeValue.toString())
                    }
                    alertDialog.dismiss() // Close the dialog after handling the click

                    applyDarkMode(isDarkModeOn) // Apply the new theme
                }

                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                    alertDialog.dismiss() // Close the dialog after handling the click
                    toggleDarkSwitch!!.isChecked = !isChecked // Revert the switch state
                }
            }
            alertDialog.show()
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

    private fun applyDarkMode(isDarkModeOn: Boolean?) {
        if (isDarkModeOn == true) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        toggleDarkSwitch!!.isChecked = isDarkModeOn ?: false
    }

    private fun logout() {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setMessage("Tem certeza?")

        alertDialogBuilder.setPositiveButton("Logout", null)
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
                mSignInClient?.signOut()?.addOnCompleteListener(requireActivity()) { goToSignIn() }

                alertDialog.dismiss() // Close the dialog after handling the click
            }

            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                alertDialog.dismiss() // Close the dialog after handling the click
            }
        }
        alertDialog.show()
    }

    private fun openAndLoadConversation(fileName: String) {
        val jsonFile = StringBuilder()

        try {
            requireContext().openFileInput(fileName).use { fis ->
                InputStreamReader(fis, Charsets.UTF_8).buffered().use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        jsonFile.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val gson = Gson()
            val conversation = gson.fromJson(jsonFile.toString(), Conversation::class.java)

            (activity as MainActivity).messageList.clear()
            (activity as MainActivity).messageList.addAll(conversation.messages)
            (activity as MainActivity).chatAdapter?.notifyDataSetChanged()
            Toast.makeText(requireContext(), "Conversa carregada", Toast.LENGTH_SHORT).show()

        } catch(e: Exception) {
            Toast.makeText(requireContext(), "Erro ao carregar conversa", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    private fun saveConversation(fileName: String, conversation: Conversation) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(conversation)

        val context = requireContext().applicationContext
        var outputStream: FileOutputStream? = null
        try {
            outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            outputStream.write(jsonString.toByteArray())
            Toast.makeText(context, "Conversa Salva", Toast.LENGTH_SHORT).show()

            // Get the file's path
            val filePath = context.getFileStreamPath(fileName)?.absolutePath ?: "Unknown path"

            Log.d("saveConversation", "File has been saved at: $filePath")
        } catch (e: Exception) {
            Toast.makeText(context, "Falha ao salvar conversa", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        } finally {
            // Ensure stream is closed even in case of exception
            try {
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteUserAccountAndData() {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
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
                                        Toast.makeText(requireContext(), "Deletado com sucesso"
                                            ,Toast.LENGTH_LONG).show()
                                        goToSignIn()
                                    } else {
                                        Toast.makeText(requireContext(), "Falha durante processo." +
                                                "Tente novamente.", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Falha durante processo." +
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
                Toast.makeText(requireContext(), "imagem não encontrada", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(requireContext(), "erro no login", Toast.LENGTH_LONG).show()
        }
    }

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
                Toast.makeText(requireContext(), "Permissão negada.Não foi possível carregar conversa.",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        view?.visibility = if (hidden) View.GONE else View.VISIBLE
    }

    private fun goToWallet() {
        val intent = Intent(requireContext(), Wallet::class.java)
        requireActivity().startActivity(intent)
    }

    private fun goToSignIn() {
        val intent = Intent(requireContext(), SignIn::class.java)
        requireActivity().startActivity(intent)
    }
}