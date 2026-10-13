package com.myPage.pdfGenerator.Activity

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.utils.Constant

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val ivBack = findViewById<ImageView>(R.id.ivBack)
        val webView = findViewById<WebView>(R.id.webView)


        ivBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        webView.settings.javaScriptEnabled = true

        webView.webViewClient = WebViewClient()



        webView.loadUrl(Constant.url)
    }
}