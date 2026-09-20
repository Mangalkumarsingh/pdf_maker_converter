package com.myPage.pdfGenerator.Activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.myPage.pdfGenerator.MainActivity
import com.myPage.pdfGenerator.databinding.ActivitySplashBinding
import com.google.android.datatransport.BuildConfig


class SplashActivity : AppCompatActivity() {
    lateinit var binding: ActivitySplashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.versionName.bringToFront()
        binding.versionName.text = "version: ${com.myPage.pdfGenerator.BuildConfig.VERSION_NAME}"
         Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
             finish()
        },3000)



    }
}