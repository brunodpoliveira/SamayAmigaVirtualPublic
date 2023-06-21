package com.internaltest.sarahchatbotmvp.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ProgressDialogFragment : DialogFragment() {

    companion object {
        private const val MESSAGE_KEY = "message"

        fun newInstance(message: String): ProgressDialogFragment {
            val dialogFragment = ProgressDialogFragment()
            val args = Bundle()
            args.putString(MESSAGE_KEY, message)
            dialogFragment.arguments = args
            return dialogFragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = arguments?.getString(MESSAGE_KEY) ?: ""
        return AlertDialog.Builder(requireActivity())
            .setMessage(message)
            .setCancelable(false)
            //.setProgressBarIndeterminate(true)
            .create()
    }
}
