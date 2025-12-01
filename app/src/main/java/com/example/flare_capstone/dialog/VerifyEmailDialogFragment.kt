package com.example.flare_capstone.dialog

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.ActivityVerifyEmailBinding
import com.example.flare_capstone.views.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

class VerifyEmailDialogFragment : DialogFragment() {

    private var _binding: ActivityVerifyEmailBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var cooldownMs = 30_000L
    private var timer: CountDownTimer? = null

    private var firestoreListener: ListenerRegistration? = null

    // 🔥 FIX: Prevent redirect after user closes dialog
    private var dialogClosed = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.RoundedDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityVerifyEmailBinding.inflate(inflater, container, false)

        val email = auth.currentUser?.email ?: ""
        binding.emailTv.text = email

        startFirestoreListener()

        binding.verifiedBtn.setOnClickListener { checkVerified() }
        binding.resendBtn.setOnClickListener { resendEmail() }

        // ❗ Fixed: closing dialog sets flag
        binding.closeBtn.setOnClickListener {
            dialogClosed = true
            dismissAllowingStateLoss()
        }

        binding.openGmailTv.setOnClickListener {
            openDefaultEmailApp()
        }


        startCooldown()
        startPolling()

        return binding.root
    }

    private fun openDefaultEmailApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No email app found on this device.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun startFirestoreListener() {
        val user = auth.currentUser ?: return

        firestoreListener = firestore.collection("users")
            .document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val status = snapshot?.getString("status") ?: return@addSnapshotListener

                // ❗ FIX: only redirect if dialog is still open
                if (status == "verified" && !dialogClosed) {
                    toast("Email verified successfully!")
                    redirect()
                }
            }
    }

    private fun startPolling() {
        val user = auth.currentUser ?: return

        pollRunnable = object : Runnable {
            override fun run() {
                user.reload().addOnSuccessListener {
                    if (user.isEmailVerified) updateVerification(user.uid)
                    else handler.postDelayed(this, 5000)
                }
            }
        }

        handler.postDelayed(pollRunnable!!, 5000)
    }

    private fun updateVerification(uid: String) {
        firestore.collection("users")
            .document(uid)
            .update(
                mapOf(
                    "status" to "verified",
                    "verifiedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                // ❗ FIX: do NOT redirect if closed
                if (!dialogClosed) {
                    toast("Email verified successfully!")
                    redirect()
                }
            }
    }

    private fun checkVerified() {
        val user = auth.currentUser ?: return toast("Session expired.")

        user.reload().addOnSuccessListener {
            if (user.isEmailVerified) updateVerification(user.uid)
            else toast("Not verified yet. Check your inbox.")
        }
    }

    private fun resendEmail() {
        val user = auth.currentUser ?: return toast("No session found.")

        auth.currentUser?.sendEmailVerification()
            ?.addOnSuccessListener {
                toast("Verification email sent to $user")
            }
            ?.addOnFailureListener { e ->
                toast("Failed to send verification: ${e.message}")
            }
    }

    private fun startCooldown() {
        binding.resendBtn.isEnabled = false
        timer?.cancel()

        timer = object : CountDownTimer(cooldownMs, 1000) {
            override fun onTick(ms: Long) {
                binding.timerTv.text = "Resend available in ${ms / 1000}s"
            }

            override fun onFinish() {
                binding.timerTv.text = ""
                binding.resendBtn.isEnabled = true
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        timer?.cancel()
        pollRunnable?.let { handler.removeCallbacks(it) }

        firestoreListener?.remove()
        firestoreListener = null

        _binding = null
    }

    private fun redirect() {
        // ❗ Prevent redirect when dialog already closed
        if (dialogClosed) return

        auth.signOut()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        dismissAllowingStateLoss()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
