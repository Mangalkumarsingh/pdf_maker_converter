package com.myPage.pdfGenerator.Activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.myPage.pdfGenerator.databinding.ActivityProfileBinding

class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarProfile.setNavigationIcon(com.myPage.pdfGenerator.R.drawable.back_img)
        binding.toolbarProfile.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
