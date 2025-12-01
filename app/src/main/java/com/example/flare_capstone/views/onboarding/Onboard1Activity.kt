package com.example.flare_capstone.views.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flare_capstone.databinding.ActivityOnboard1Binding
import com.example.flare_capstone.views.activity.FirefighterActivity
import com.example.flare_capstone.views.activity.MainActivity
import com.example.flare_capstone.views.activity.UserActivity
import com.example.flare_capstone.views.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class Onboard1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboard1Binding
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    private val logoutTimeLimit: Long = 30 * 60 * 1000
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOnboard1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 🔥 AUTO-LOGIN CHECK
        checkAutoLogin()

        binding.getStartedButton.setOnClickListener {
            startActivity(Intent(this, Onboard2Activity::class.java))
            finish()
        }

        binding.skipButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        handler.postDelayed(logoutRunnable, logoutTimeLimit)
    }

    private fun checkAutoLogin() {
        val user = auth.currentUser ?: return

        user.reload().addOnSuccessListener {

            // ❌ Not verified in Firebase → force to login screen
            if (!user.isEmailVerified) {
                Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_SHORT).show()
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return@addOnSuccessListener
            }

            // 🔥 FETCH FIRESTORE
            firestore.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { doc ->

                    if (!doc.exists()) {
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                        return@addOnSuccessListener
                    }

                    val status = doc.getString("status") ?: "unverified"

                    // ❌ Account pending
                    if (status != "verified") {
                        Toast.makeText(this, "Account pending verification.", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                        return@addOnSuccessListener
                    }

                    // 🔥 AUTO-SET verifiedAt IF NULL
                    if (doc.get("verifiedAt") == null) {
                        firestore.collection("users")
                            .document(user.uid)
                            .update("verifiedAt", Timestamp.now())
                    }

                    // 🔥 FIRE STATION ACCOUNTS
                    val firefighterEmails = listOf(
                        "tcwestfiresubstation@gmail.com",
                        "lafilipinafire@gmail.com",
                        "bfp_tagumcity@yahoo.com"
                    )

                    if (firefighterEmails.contains(user.email)) {
                        Toast.makeText(this, "Welcome back, Firefighter", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, FirefighterActivity::class.java))
                        finish()
                        return@addOnSuccessListener
                    }

                    // 🔥 NORMAL USERS
                    Toast.makeText(this, "Welcome back", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, UserActivity::class.java))
                    finish()
                }
        }
    }

    private val logoutRunnable = Runnable {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            auth.signOut()
            Toast.makeText(this, "Logged out due to inactivity", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(logoutRunnable)
    }
}
