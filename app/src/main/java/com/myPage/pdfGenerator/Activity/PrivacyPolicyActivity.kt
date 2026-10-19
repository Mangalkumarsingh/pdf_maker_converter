package com.myPage.pdfGenerator.Activity

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.utils.Constant
import com.myPage.pdfGenerator.utils.Constant.url

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val ivBack = findViewById<ImageView>(R.id.ivBack)
        val webView = findViewById<WebView>(R.id.webView)

        webView.settings.javaScriptEnabled = true

        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.textZoom = 175

        webView.webViewClient = WebViewClient()

        ivBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        webView.loadUrl(url)
    }
}