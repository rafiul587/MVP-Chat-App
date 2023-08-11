package com.example.mvpchatapplication.ui.profile

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.utils.DialogListener
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText

class PasswordEditDialog(private val listener: DialogListener, val password: String) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_password_edit, null)
        val editTextPassword = view.findViewById<TextInputEditText>(R.id.editTextPassword)
        editTextPassword.setText(password)
        val positiveBtn = view.findViewById<Button>(R.id.btnPositiveButton)
        val negativeBtn = view.findViewById<Button>(R.id.btnNegativeButton)
        val progressBar = view.findViewById<CircularProgressIndicator>(R.id.progressBar)
        positiveBtn.setOnClickListener {
            val passwordText = editTextPassword.text.toString()
            // Add validation for email if needed
            if (passwordText.isEmpty()) {
                editTextPassword.error = "Password can't be empty!"
                return@setOnClickListener
            } else if (passwordText.length < 6) {
                editTextPassword.error = "Minimum password length is 6!"
                return@setOnClickListener
            }
            progressBar.isVisible = true
            positiveBtn.isEnabled = false
            listener.onPasswordChanged(passwordText)
        }
        negativeBtn.setOnClickListener {
            dismiss()
        }
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(view)
                .create()
        isCancelable = false
        return dialog
    }
}
