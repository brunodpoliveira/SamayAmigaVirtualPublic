package com.internaltest.sarahchatbotmvp.utils

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.internaltest.sarahchatbotmvp.data.FirestoreRepo
import com.internaltest.sarahchatbotmvp.data.SaveLoadConversationManager
import com.internaltest.sarahchatbotmvp.data.WalletRepo
import com.internaltest.sarahchatbotmvp.models.Conversation
import com.internaltest.sarahchatbotmvp.ui.main.ProgressDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object DialogUtils {

    private var progressDialogFragment: ProgressDialogFragment? = null

    fun showProgressDialog(
        fragmentManager: FragmentManager,
        message: String
    ) {
        if(progressDialogFragment?.isAdded != true) {
            progressDialogFragment = ProgressDialogFragment.newInstance(message).apply {
                isCancelable = false
            }
            progressDialogFragment?.show(fragmentManager, "progressDialog")
        }
    }

    fun dismissProgressDialog() {
        progressDialogFragment?.dismissAllowingStateLoss()
        progressDialogFragment = null
    }

    fun isProgressDialogVisible(): Boolean {
        return progressDialogFragment?.dialog?.isShowing == true
    }

    private fun createAlertDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String,
        negativeButtonText: String,
        positiveButtonAction: (DialogInterface, Int) -> Unit,
        negativeButtonAction: (DialogInterface, Int) -> Unit
    ): AlertDialog {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText, positiveButtonAction)
            .setNegativeButton(negativeButtonText, negativeButtonAction)
            .create()

        val currentTheme = AppCompatDelegate.getDefaultNightMode()

        // Set the text color for the positive and negative buttons based on the current theme
        val buttonTextColor = if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        alertDialog.setOnShowListener {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(buttonTextColor)
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(buttonTextColor)
        }
        return alertDialog
    }

    fun showDialogConfirmReportChat(
        context: Context,
        onSuccess: () -> Unit
    ) {
        val dialog = createAlertDialog(
            context,
            "Reportar Chat",
            "Quer mesmo reportar essa conversa? Reportes falsos podem resultar em banimento do app",
            "Reportar",
            "Desistir",
            { dialog, _ ->
                dialog.dismiss()
                onSuccess()
            },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.show()
    }
    fun showDialogReportChat(
        context: Context,
        onSuccess: () -> Unit
    ) {
        val dialog = createAlertDialog(
            context,
            "Reportar Chat",
            "Tem certeza que deseja reportar essa conversa?",
            "Sim",
            "Não",
            { dialog, _ ->
                dialog.dismiss()
                onSuccess()
            },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.show()
    }

    fun showToggleDarkModeAlertDialog(
        context: Context,
        isChecked: Boolean,
        toggleDarkSwitch: SwitchCompat,
        applyDarkMode: (Boolean) -> Unit,
        newDarkModeValue: () -> Boolean
    ) {
        val dialog = createAlertDialog(
            context,
            "Tem certeza?",
            "Todo o histórico de conversa será apagado ao trocar tema",
            "Sim",
            "Não",
            { _, _ ->
                // Toggle the dark mode value and update it in Firestore
                val newValue = newDarkModeValue()
                applyDarkMode(newValue) // Apply the new theme
            },
            { dialog, _ ->
                dialog.dismiss()
                toggleDarkSwitch.isChecked = isChecked // Revert the switch state
            }
        )
        dialog.show()
    }

    private fun showUpdateAlertDialog(context: Context, appPackageName: String) {
        val dialog = createAlertDialog(
            context,
            "Atualização Disponível",
            "Uma nova versão do app está disponível. " +
                    "Favor atualize para a última versão.",
            "Atualizar",
            "Agora Não",
            { _, _ ->
                // Redirect the user to the app's page in the Google Play Store
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=$appPackageName")
                        )
                    )
                } catch (e: android.content.ActivityNotFoundException) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                        )
                    )
                }
            },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.setOnCancelListener {
            // Handle cancellation here if needed
        }

        dialog.show()
    }

    fun showLogoutAlertDialog(context: Context, signOut: () -> Unit) {
        val dialog = createAlertDialog(
            context,
            "Tem certeza?",
            "Todo o histórico de conversa será apagado ao fazer logout",
            "Logout",
            "Voltar",
            { _, _ ->
                // Perform sign out and invoke passed action afterwards
                signOut()
            },
            { dialog, _ -> dialog.dismiss() }
        )

        dialog.show()
    }

    //TODO atualizar para ser condição ser verificada uma vez no dia; usar logica da msg diaria p isso
    fun checkForUpdate(context: Context, packageName: String) {
        val firestore = FirebaseFirestore.getInstance()
        val appVersionRef = firestore.collection("app_version").document("latest")
        appVersionRef.get().addOnSuccessListener { document ->
            if (document != null) {
                Log.d("Firestore checkForUpdate", "Fetched document: ${document.id}")

                val latestVersion = document.getString("latest_version")
                if (latestVersion != null) {
                    Log.d("Firestore checkForUpdate", "Fetched latest_version: $latestVersion")

                    if (isUpdateAvailable(context, latestVersion, packageName)) {
                        showUpdateAlertDialog(context, packageName)
                    }
                } else {
                    Log.d("Firestore checkForUpdate", "latest_version field not found.")
                }
            } else {
                Log.d("Firestore checkForUpdate", "Document not found.")
            }
        }.addOnFailureListener { exception ->
            Log.e("Firestore checkForUpdate", "Error fetching document: ", exception)
        }
    }

    private fun isUpdateAvailable(
        context: Context,
        latestVersion: String,
        packageName: String
    ): Boolean {
        val currentVersion: String
        try {
            currentVersion = context.packageManager.getPackageInfo(packageName, 0).versionName
            Log.i("currentVersion", currentVersion)
        } catch (e: PackageManager.NameNotFoundException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            e.printStackTrace()
            return false
        }
        return currentVersion != latestVersion
    }

    fun showNoCreditsAlertDialog(context: Context, action: () -> Unit) {
        val dialog = createAlertDialog(
            context,
            "Você está sem créditos",
            "Compre mais ou aguarde 24hrs para ter mais",
            "Comprar créditos",
            "OK",
            { _, _ -> action() },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.show()
    }

    //TODO não deletar
    fun showFeedbackPopup(context: Context, action: () -> Unit) {
        val dialog = createAlertDialog(
            context,
            "Responder Formulário?",
            "Ganhe 7 dias de assinatura Premium grátis" +
                    " respondendo esse formulário",
            "Responder formulário",
            "Agora não",
            { _, _ -> action() },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.show()
    }

    fun showSubscriptionWarningPopup(context: Context) {
        val dialog = createAlertDialog(
            context,
            "Ativação da Assinatura:",
            "Por favor note que pode demorar até 5 minutos para ativar sua assinatura",
            "OK",
            "",
            { dialog, _ -> dialog.dismiss() },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.show()
    }

    fun showErrorDialogAndNavigateToLogin(context: Context, action: () -> Unit) {
        val dialog = createAlertDialog(
            context,
            "Sessão Expirada",
            "Sua sessão expirou. Você será redirecionado a tela de login. " +
                    "Seus dados estão salvos, com exceção do histórico de conversa",
            "OK",
            "",
            { _, _ -> action() },
            { _, _ -> action() }
        )
        dialog.show()
    }

    suspend fun dailyLoginReward(
        context: Context,
        firebaseRepo: FirestoreRepo,
        creditsTextView: TextView?,
        setIsRewardPopupShown: (Boolean) -> Unit,
        isRewardPopupShown: Boolean,
        isDailyLoginRewardRunning: Boolean
    ) {
        if (isRewardPopupShown || isDailyLoginRewardRunning) return

        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH] + 1
        val day = calendar[Calendar.DAY_OF_MONTH]
        val todayString = String.format("%04d%02d%02d", year, month, day)

        val lastLoginDay = firebaseRepo.dailyLoginDay.first()
        Log.i("lastLoginDay.first()", lastLoginDay)

        var isOldDate = false
        val lastLoginDate = try {
            if (lastLoginDay.isNotEmpty()) {
                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(lastLoginDay)
            } else {
                null
            }
        } catch (e: ParseException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("lastLoginDate", "Error parsing date: $e")
            isOldDate = true
            null
        }

        if (lastLoginDay.isEmpty() || isOldDate || lastLoginDate == null) {
            // New user or old date format, show the reward popup
            Log.i("showrewardpopup", "isempty or old date format")
            Log.i("lastLoginDay", lastLoginDay)
            showRewardPopup(
                context,
                firebaseRepo,
                creditsTextView,
                setIsRewardPopupShown,
                isRewardPopupShown
            )
            if (isOldDate) {
                // Delete the old date
                Log.i("showrewardpopup", "isOldDate")
                firebaseRepo.setDailyLoginDay("ddd")
            }
        } else {
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(todayString)
            val diffInMillis = currentDate!!.time - lastLoginDate.time
            val diffInHours = TimeUnit.MILLISECONDS.toHours(diffInMillis)

            if (
                diffInHours >= 24 &&
                isTimeAutomatic(context) &&
                isZoneAutomatic(context) &&
                lastLoginDay != todayString &&
                !isRewardPopupShown
            ) {
                Log.i("showrewardpopup", "lastlogindate")
                showRewardPopup(
                    context,
                    firebaseRepo,
                    creditsTextView,
                    setIsRewardPopupShown,
                    isRewardPopupShown
                )
            } else {
                Toast.makeText(
                    context, "Você já pegou seus créditos grátis", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isTimeAutomatic(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AUTO_TIME,
            0
        ) == 1
    }

    private fun isZoneAutomatic(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AUTO_TIME_ZONE,
            0
        ) == 1
    }

    private fun showRewardPopup(
        context: Context,
        firestoreRepo: FirestoreRepo,
        creditsTextView: TextView?,
        setIsRewardPopupShown: (Boolean) -> Unit,
        isRewardPopupShown: Boolean
    ) {
        if (isRewardPopupShown) return

        setIsRewardPopupShown(true)

        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH] + 1
        val day = calendar[Calendar.DAY_OF_MONTH]
        val todayString = String.format("%04d%02d%02d", year, month, day)
        val walletRepo = WalletRepo()

        val alertDialog = createAlertDialog(
            context,
            "Créditos grátis",
            "Aperte aqui para créditos grátis",
            "Pegar Créditos",
            "Cancelar",
            { _, _ ->
                Toast.makeText(
                    context, "Créditos grátis adicionados a sua conta!",
                    Toast.LENGTH_LONG
                ).show()

                val dialogScope = CoroutineScope(Dispatchers.Main)

                dialogScope.launch {
                    walletRepo.addCredits(15)
                    firestoreRepo.setDailyLoginDay(todayString) // Update last_login_date
                    walletRepo.getCredits().collect { updatedCredits ->
                        Log.i("updatedCredits", updatedCredits.toString())
                        if (creditsTextView != null) {
                            creditsTextView.text = updatedCredits.toString()
                        }
                        Log.i("credits (reward)", updatedCredits.toString())
                    }
                }
            },
            { alertDialog, _ ->
                alertDialog.dismiss()
                setIsRewardPopupShown(false)
            }
        )
        alertDialog.setOnCancelListener {
            setIsRewardPopupShown(false)
        }
        alertDialog.show()
    }

    fun showSaveConversationDialog(
        context: Context,
        conversation: Conversation,
        saveConversation: (String, Conversation, Boolean) -> Unit
    ) {

        val inputField = EditText(context)
        val alertDialogB = AlertDialog.Builder(context)
            .setTitle("Digite nome do arquivo")
            .setView(inputField)
            .setPositiveButton("OK") { _, _ ->
                val conversationName = inputField.text.toString()
                if (conversationName.isNotEmpty()) {
                    val fileName = "$conversationName-${System.currentTimeMillis()}.json"

                    // Choose where to save the conversation.
                    val alertDialog = AlertDialog.Builder(context)
                        .setTitle("Escolha onde salvar")
                        .setPositiveButton("Dispositivo Local") { _, _ ->
                            saveConversation(fileName, conversation, false)
                        }
                        .setNegativeButton("Nuvem") { _, _ ->
                            saveConversation(fileName, conversation, true)

                        }
                        .setNeutralButton("Cancelar", null)
                        .create()

                    alertDialog.setOnShowListener {
                        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setTextColor(
                                    ContextCompat.getColor(
                                        context,
                                        android.R.color.white
                                    )
                                )
                            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                                .setTextColor(
                                    ContextCompat.getColor(
                                        context,
                                        android.R.color.white
                                    )
                                )
                            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                .setTextColor(
                                    ContextCompat.getColor(
                                        context,
                                        android.R.color.white
                                    )
                                )
                        } else {
                            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setTextColor(
                                    ContextCompat.getColor(
                                        context,
                                        android.R.color.black
                                    )
                                )
                            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                                .setTextColor(
                                    ContextCompat.getColor(
                                        context,
                                        android.R.color.black
                                    )
                                )
                            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                .setTextColor(
                                    ContextCompat.getColor(
                                        context,
                                        android.R.color.black
                                    )
                                )
                        }

                    }

                    alertDialog.show()
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        alertDialogB.setOnShowListener {
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                alertDialogB.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
                alertDialogB.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
            } else {
                alertDialogB.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
                alertDialogB.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }

        }
        alertDialogB.show()
    }

    private fun openLocalFilePicker(
        context: Context, conversationManager: SaveLoadConversationManager,
        openFilePicker: (Boolean) -> Unit
    ) {
        val folderName = "conversas_samay_vr"
        val folderPath = File(context.filesDir, folderName)

        // Get a list of all .json files in the folder
        val files = folderPath.listFiles { _, name -> name.endsWith(".json") }

        val fileNames = files?.map { it.nameWithoutExtension }

        if (fileNames.isNullOrEmpty()) {
            Toast.makeText(context, "Nenhuma conversa encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        // Show custom file picker dialog
        val dialogTitle = "Escolha conversa"

        val alertDialog = AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setItems(fileNames.toTypedArray()) { _, _ ->
                openFilePicker.invoke(false)
                conversationManager.loadConversation(false)
            }
            .setNegativeButton("Cancel", null)
            .create()

        alertDialog.show()
    }

    fun showLoadConversationDialog(
        context: Context,
        conversationManager: SaveLoadConversationManager,
        openFilePicker: (Boolean) -> Unit
    ) {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Escolha de onde carregar sua conversa")
            .setPositiveButton("Dispositivo Local") { _, _ ->
                openLocalFilePicker(context, conversationManager, openFilePicker)
            }
            .setNegativeButton("Nuvem") { _, _ ->
                openFilePicker(true)
            }
            .setNeutralButton("Cancelar", null)

            .create()

        // Definindo cores para o texto
        alertDialog.setOnShowListener {
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
                alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
            } else {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
                alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }
        }

        alertDialog.show()
    }
    
    fun showExitDialog(
        context: Context,
        onSave: () -> Unit,
        onQuitWithoutSaving: () -> Unit,
        onCancel: () -> Unit
    ) {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Você quer salvar antes de sair?")
            .setMessage("Selecione uma opção:")
            .setPositiveButton("Sair") { dialog, _ ->
                onSave()
                dialog.dismiss()
            }
            .setNegativeButton("Sair s/ salvar") { _, _ ->
                onQuitWithoutSaving()
            }
            .setNeutralButton("Não sair") { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .create()

        // Definindo cores para o texto
        alertDialog.setOnShowListener {
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
                alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.white))
            } else {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
                alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }

        }
        alertDialog.show()
    }

    fun showDeleteAccountConfirmationDialog(
        context: Context,
        conversationManager: SaveLoadConversationManager,
        auth: FirebaseAuth,
        onSuccess: () -> Unit
    ) {
        val dialog = createAlertDialog(
            context,
            "AVISO",
            "Você tem certeza que deseja deletar sua conta e dados? Esta ação não pode ser desfeita",
            "Deletar",
            "Cancelar",
            { _, _ ->
                // Deletion code
                val user = auth.currentUser
                if (user != null) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users")
                        .document(user.uid)
                        .delete()
                        .addOnSuccessListener {
                            conversationManager.deleteConversationFolder()
                            // Delete user's account from FirebaseAuth
                            user.delete()
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(
                                            context,
                                            "Deletado com sucesso",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onSuccess()
                                    } else {
                                        Log.e("FirebaseAuth", "Error deleting user account: ${task.exception}")
                                        Toast.makeText(
                                            context,
                                            "Falha durante processo. Tente novamente.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        }
                        .addOnFailureListener {
                            Log.e("Firestore", "Error deleting user data: $it")
                            Toast.makeText(
                                context,
                                "Falha durante processo. Tente novamente.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                } else {
                    Log.e("FirebaseAuth", "Current user is null")
                }
            },
            { dialog, _ -> dialog.dismiss() }
        )
        dialog.show()
    }
}