package com.example.flare_capstone.views.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.flare_capstone.R
import com.example.flare_capstone.data.database.UserSession
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()
        prefManager = PrefManager(this)
        UserSession.init(this)

        Handler(Looper.getMainLooper()).postDelayed({
            checkFirstTime()
        }, 2000)
    }

    private fun checkFirstTime() {
        if (prefManager.isFirstTimeLaunch()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        } else {
            checkSessionAndNavigate()
        }
    }

    private fun checkSessionAndNavigate() {
        val firebaseUser = auth.currentUser

        // If Firebase has no user, session is invalid → clear & go to Auth
        if (firebaseUser == null) {
            UserSession.clear()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // If we never saved a session, also go to Auth
        if (!UserSession.isLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        when (UserSession.getRole()) {
            "user" -> {
                startActivity(Intent(this, UserActivity::class.java))
            }
            "investigator" -> {
                startActivity(Intent(this, InvestigatorActivity::class.java))
            }
            "firefighter" -> {
                startActivity(Intent(this, FirefighterActivity::class.java))
            }
            else -> {
                // Something weird, clear session
                UserSession.clear()
                startActivity(Intent(this, AuthActivity::class.java))
            }
        }
        finish()
    }
}
