package com.internaltest.sarahchatbotmvp.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ProgressDialogFragment : DialogFragment() {

    companion object {
        private const val MESSAGE_KEY = "message"

        fun newInstance(message: String): ProgressDialogFragment {
            return ProgressDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(MESSAGE_KEY, message)
                }
                isCancelable = false  // This ensures the dialog is not cancelable
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = arguments?.getString(MESSAGE_KEY) ?: ""
        return AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setCancelable(false)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)  // Prevent dismissing when touching outside
    }
}