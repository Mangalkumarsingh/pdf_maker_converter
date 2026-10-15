package com.myPage.pdfGenerator.Activity

import android.graphics.Color
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.imageview.ShapeableImageView
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.databinding.ActivityMainBinding
import com.myPage.pdfGenerator.fragment.HomeFragment
import com.myPage.pdfGenerator.fragment.ProfileFragment
import com.myPage.pdfGenerator.fragment.RecentFragment

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val TOOL_PDF_TO_DOCS = 1
        const val TOOL_DOCS_TO_PDF = 2
        const val TOOL_IMG_TO_PDF = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.setPadding(0, insets.top, 0, 0)

            binding.bottomNav.setPadding(0, 0, 0, insets.bottom)

            windowInsets
        }

        val nav = binding.toolbar.findViewById<ShapeableImageView>(R.id.ivNav)
        nav.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_recent -> {
                    loadFragment(RecentFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }


        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}