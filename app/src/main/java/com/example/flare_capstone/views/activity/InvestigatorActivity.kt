package com.example.flare_capstone.views.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.ActivityInvestigatorBinding
import np.com.susanthapa.curved_bottom_navigation.CbnMenuItem

class InvestigatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInvestigatorBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false) // let us handle insets
        binding = ActivityInvestigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupBottomNavigation()
    }

    private fun applyWindowInsets() {
        // Apply insets to root, then adjust children (nav host + bottom nav)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Push content down from status bar if needed
            binding.navHostFragment.setPadding(
                0,
                systemBars.top,
                0,
                0
            )

            // Push bottom nav above the system nav buttons (gesture / 3-button)
            binding.investigatorNavView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = systemBars.bottom
            }

            insets
        }
    }

    private fun setupBottomNavigation() {
        // Get the nav controller from the FragmentContainerView
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Bottom nav items matching your fragments & nav graph destinations
        val menuItems = arrayOf(
            CbnMenuItem(R.drawable.ic_home, R.drawable.avd_home, R.id.inHomeFragment),
            CbnMenuItem(R.drawable.ic_services, R.drawable.avd_services, R.id.reportFlow),
            CbnMenuItem(R.drawable.ic_dashboard, R.drawable.avd_dashboard, R.id.inInboxFragment),
            CbnMenuItem(R.drawable.ic_profile, R.drawable.avd_profile, R.id.inPorfileFragment)
        )

        // Setup menu (0 = default selected index)
        binding.investigatorNavView.setMenuItems(menuItems, 0)

        // Connect bottom nav to navController
        binding.investigatorNavView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
