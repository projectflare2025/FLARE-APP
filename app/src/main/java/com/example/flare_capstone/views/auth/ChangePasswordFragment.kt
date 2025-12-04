package com.example.flare_capstone.views.auth

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.FragmentChangePasswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {

    /* ---------------- View / Auth ---------------- */
    private lateinit var binding: FragmentChangePasswordBinding
    private lateinit var auth: FirebaseAuth

    /* ---------------- Connectivity ---------------- */
    private lateinit var connectivityManager: ConnectivityManager
    private var loadingDialog: AlertDialog? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            activity?.runOnUiThread { hideLoadingDialog() }
        }
        override fun onLost(network: Network) {
            activity?.runOnUiThread { showLoadingDialog("No internet connection") }
        }
    }

    /* =========================================================
     * Lifecycle
     * ========================================================= */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChangePasswordBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        connectivityManager = activity?.getSystemService(ConnectivityManager::class.java)!!

        // Initial network state
        if (!isConnected()) showLoadingDialog("No internet connection")

        // Register network listener
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // Back button
        binding.back.setOnClickListener { requireActivity().onBackPressed() }

        // Save action
        binding.saveButton.setOnClickListener { onChangePasswordClicked() }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog?.dismiss()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    /* =========================================================
     * UI Actions
     * ========================================================= */
    private fun onChangePasswordClicked() {
        val currentPassword = binding.currentPassword.text.toString().trim()
        val newPassword = binding.newPassword.text.toString().trim()
        val confirmPassword = binding.confirmPassword.text.toString().trim()

        // Client-side validation
        if (currentPassword.isEmpty()) {
            binding.currentPassword.error = "Please enter your current password"
            return
        }
        if (newPassword.isEmpty()) {
            binding.newPassword.error = "Please enter a new password"
            return
        }
        if (confirmPassword.isEmpty()) {
            binding.confirmPassword.error = "Please confirm your new password"
            return
        }
        if (newPassword != confirmPassword) {
            binding.confirmPassword.error = "Passwords do not match"
            return
        }
        if (!isConnected()) {
            showToast("No internet connection")
            return
        }

        val user = auth.currentUser
        if (user == null) {
            showToast("You need to be signed in.")
            return
        }
        val currentEmail = user.email
        if (currentEmail.isNullOrEmpty()) {
            showToast("Current email is missing.")
            return
        }

        // Lock UI while processing
        setSaving(true)

        // Reauthenticate with current password then update
        val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        showToast("Password changed successfully")
                        clearPasswordFields()
                    }
                    .addOnFailureListener { e ->
                        showToast("Failed to change password: ${e.message}")
                    }
                    .addOnCompleteListener { setSaving(false) }
            }
            .addOnFailureListener { e ->
                setSaving(false)
                if (e is FirebaseAuthRecentLoginRequiredException) {
                    showToast("Reauthentication required. Please log in again.")
                } else {
                    showToast("Authentication failed: ${e.message}")
                }
            }
    }

    /* =========================================================
     * Helpers: UI / State
     * ========================================================= */
    private fun setSaving(saving: Boolean) {
        binding.saveButton.isEnabled = !saving
        if (saving) {
            showLoadingDialog("Updating password…")
        } else {
            hideLoadingDialog()
        }
    }

    private fun clearPasswordFields() {
        binding.currentPassword.text?.clear()
        binding.newPassword.text?.clear()
        binding.confirmPassword.text?.clear()
    }

    private fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /* =========================================================
     * Helpers: Connectivity
     * ========================================================= */
    private fun isConnected(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /* =========================================================
     * Helpers: Loading Dialog
     * ========================================================= */
    private fun showLoadingDialog(message: String = "Please wait…") {
        if (loadingDialog == null) {
            val view = layoutInflater.inflate(R.layout.custom_loading_dialog, null)
            loadingDialog = AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
        loadingDialog?.findViewById<TextView>(R.id.loading_message)?.text = message
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }
}
