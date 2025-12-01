package com.example.flare_capstone.views.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flare_capstone.views.activity.UserActivity
import com.example.flare_capstone.R
import com.example.flare_capstone.dialog.VerifyEmailDialogFragment
import com.example.flare_capstone.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private var verificationListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupPasswordToggle(binding.password)

        binding.loginButton.setOnClickListener { onLoginClicked() }
        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
        binding.forgotPassword.setOnClickListener { onForgotPassword() }

        handleResetIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        attachVerificationListener()
    }

    override fun onStop() {
        super.onStop()
        verificationListener?.remove()
    }

    private fun attachVerificationListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        verificationListener = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snap, err ->

                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener

                val status = snap.getString("status") ?: return@addSnapshotListener

                // If Firestore status becomes "verified", close dialog & go to dashboard
                if (status == "verified") {

                    // Auto-set verifiedAt if missing
                    if (snap.getTimestamp("verifiedAt") == null) {
                        firestore.collection("users")
                            .document(uid)
                            .update("verifiedAt", Timestamp.now())
                    }

                    // Close dialog if open
                    val dialog = supportFragmentManager.findFragmentByTag("VerifyEmailDialog")
                    if (dialog is VerifyEmailDialogFragment) {
                        dialog.dismissAllowingStateLoss()
                    }

                    routeToDashboard()
                }
            }
    }

    // ======================================================
    // 🔐 LOGIN FLOW
    // ======================================================
    private fun onLoginClicked() {
        val email = binding.email.text.toString().trim().lowercase()
        val password = binding.password.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Please fill in both fields")
            return
        }

        setLoginEnabled(false)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                attachVerificationListener()
                checkEmailStatus() }
            .addOnFailureListener { e ->
                setLoginEnabled(true)
                toast(
                    when (e) {
                        is FirebaseAuthInvalidCredentialsException -> "Incorrect password."
                        is FirebaseAuthInvalidUserException -> "Account not found."
                        else -> e.message ?: "Login failed."
                    }
                )
            }
    }

    // ======================================================
    // ✉️ CHECK VERIFIED OR NOT
    // ======================================================
    private fun checkEmailStatus() {
        val user = auth.currentUser ?: return

        user.reload().addOnSuccessListener {

            if (user.isEmailVerified) {

                val userDoc = firestore.collection("users").document(user.uid)

                // 🔥 Update both status and verifiedAt timestamp
                userDoc.update(
                    mapOf(
                        "status" to "verified",
                        "verifiedAt" to Timestamp.now()
                    )
                )

                loginVerifiedUser(user.uid)
            } else {
                showVerifyDialogOnly()
                setLoginEnabled(true)
            }
        }
    }



    // ======================================================
    // 🟢 VERIFIED USERS LOGIN
    // ======================================================
    private fun loginVerifiedUser(uid: String) {
        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    toast("Account data missing.")
                    auth.signOut()
                    setLoginEnabled(true)
                    return@addOnSuccessListener
                }

                val status = doc.getString("status") ?: "unverified"
                if (status != "verified") {
                    toast("Your account is still pending verification.")
                    setLoginEnabled(true)
                    return@addOnSuccessListener
                }

                routeToDashboard()
            }
            .addOnFailureListener {
                toast("Failed to fetch user: ${it.message}")
                setLoginEnabled(true)
            }
    }

    // ======================================================
    // 📩 SHOW VERIFY DIALOG (Firestore Version)
    // ======================================================
    private fun showVerifyDialogOnly() {
        val dialog = VerifyEmailDialogFragment()
        dialog.show(supportFragmentManager, "VerifyEmailDialog")
    }

    // ======================================================
    // 🧭 NAVIGATION
    // ======================================================
    private fun routeToDashboard() {
        startActivity(Intent(this, UserActivity::class.java))
        finish()
    }

    private fun setLoginEnabled(enabled: Boolean) {
        binding.loginButton.isEnabled = enabled
        binding.loginButton.alpha = if (enabled) 1f else 0.6f
    }

    // ======================================================
    // 🔑 PASSWORD RESET
    // ======================================================
    private fun onForgotPassword() {
        val email = binding.email.text.toString().trim().lowercase()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Enter a valid email.")
            return
        }

        // Disable button for 100 seconds
        startForgotPasswordCooldown()

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                toast("Reset link sent to $email")
            }
            .addOnFailureListener {
                toast("Reset failed: ${it.message}")
                // If failed, re-enable immediately
                resetForgotPasswordButton()
            }
    }
    private fun startForgotPasswordCooldown() {
        binding.forgotPassword.isEnabled = false

        object : CountDownTimer(100000, 1000) { // 100 sec = 100000 ms
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.forgotPassword.text = "Try again in ${seconds}s"
                binding.forgotPassword.setTextColor(getColor(R.color.gray))
            }

            override fun onFinish() {
                resetForgotPasswordButton()
            }
        }.start()
    }

    private fun resetForgotPasswordButton() {
        binding.forgotPassword.isEnabled = true
        binding.forgotPassword.text = "Forgot password?"
        binding.forgotPassword.setTextColor(getColor(R.color.white))
    }



    private fun handleResetIntent(intent: Intent) {
        val data: Uri? = intent.data ?: return
        val mode = data?.getQueryParameter("mode")
        val oobCode = data?.getQueryParameter("oobCode")

        if (mode != "resetPassword" || oobCode.isNullOrBlank()) return

        auth.verifyPasswordResetCode(oobCode)
            .addOnFailureListener {
                toast("Invalid or expired reset link.")
            }
    }

    // ======================================================
    // 👁 PASSWORD TOGGLE
    // ======================================================
    private fun setupPasswordToggle(editText: EditText) {
        val visibleIcon = R.drawable.ic_visibility
        val hiddenIcon = R.drawable.ic_visibility_off

        editText.transformationMethod = PasswordTransformationMethod.getInstance()
        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, hiddenIcon, 0)

        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = editText.compoundDrawables[2]
                if (drawableEnd != null &&
                    event.rawX >= (editText.right - drawableEnd.bounds.width())
                ) {
                    if (editText.transformationMethod is PasswordTransformationMethod) {
                        editText.transformationMethod = null
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, visibleIcon, 0)
                    } else {
                        editText.transformationMethod = PasswordTransformationMethod.getInstance()
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, hiddenIcon, 0)
                    }
                    editText.setSelection(editText.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}