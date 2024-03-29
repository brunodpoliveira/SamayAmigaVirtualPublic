package com.internaltest.sarahchatbotmvp.utils

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.data.SaveLoadConversationManager
import com.internaltest.sarahchatbotmvp.models.Conversation
import com.internaltest.sarahchatbotmvp.ui.main.ProgressDialogFragment
import java.io.File

object DialogUtils {

    private var progressDialogFragment: ProgressDialogFragment? = null

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
    )  {

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

    fun showSubscriptionWarningPopup(context: Context, action: () -> Unit) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_signature)
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.decorView?.setPadding(16, 0, 16, 0)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnClose = dialog.findViewById<View>(R.id.close)
        val btnCancel = dialog.findViewById<View>(R.id.tv_btn_cancel)
        val btnOk = dialog.findViewById<View>(R.id.cv_btn_ok)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnOk.setOnClickListener {
            action()
            dialog.dismiss()
        }
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