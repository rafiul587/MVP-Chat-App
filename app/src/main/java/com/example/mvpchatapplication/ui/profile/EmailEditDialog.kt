package com.example.mvpchatapplication.ui.profile

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.utils.DialogListener
import com.google.android.material.progressindicator.CircularProgressIndicator

class EmailEditDialog(private val listener: DialogListener, val email: String) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_email_edit, null)
        val editTextEmail = view.findViewById<EditText>(R.id.editTextEmail)
        val positiveBtn = view.findViewById<Button>(R.id.btnPositiveButton)
        val negativeBtn = view.findViewById<Button>(R.id.btnNegativeButton)
        val progressBar = view.findViewById<CircularProgressIndicator>(R.id.progressBar)
        editTextEmail.setText(email)
        positiveBtn.setOnClickListener {
            val emailText = editTextEmail.text.toString()
            // Add validation for email if needed
            if (email.isEmpty()) {
                editTextEmail.error = "Email can't be empty!"
                return@setOnClickListener
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                editTextEmail.error = "Invalid email format"
                return@setOnClickListener
            }
            progressBar.isVisible = true
            positiveBtn.isEnabled = false
            listener.onEmailChanged(emailText)
        }
        negativeBtn.setOnClickListener {
            dismiss()
        }
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Change Email")
                .setView(view)
                .create()
        isCancelable = false
        return dialog
    }
}
