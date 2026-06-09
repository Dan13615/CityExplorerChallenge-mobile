package com.example.cityexplorerchallenge

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etUsername = view.findViewById<EditText>(R.id.et_username)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val btnLogin = view.findViewById<Button>(R.id.btn_login)
        val btnRegister = view.findViewById<Button>(R.id.btn_register)
        val progressBar = view.findViewById<ProgressBar>(R.id.login_progress)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                authViewModel.login(username, password)
            }
        }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                authViewModel.register(username, password)
            }
        }

        authViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            btnLogin.isEnabled = !loading
            btnRegister.isEnabled = !loading
        }

        authViewModel.authState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthViewModel.AuthResult.Success -> {
                    Toast.makeText(context, "Welcome, ${result.username}!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_login_to_main)
                }
                is AuthViewModel.AuthResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
