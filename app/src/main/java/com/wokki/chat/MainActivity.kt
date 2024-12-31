package com.wokki.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("WebViewPrefs", MODE_PRIVATE)

        // Find the WebView by ID from your XML
        webView = findViewById(R.id.webView)

        // Enable JavaScript
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true

        // Enable LocalStorage
        webSettings.domStorageEnabled = true

        // Set up the WebViewClient to handle links inside the WebView
        webView.webViewClient = MyWebViewClient()

        // Load the last visited URL from SharedPreferences or a default URL
        val lastVisitedUrl = sharedPreferences.getString("lastVisitedUrl", "https://levgames.nl/jonazwetsloot/chat/api/")
        webView.loadUrl(lastVisitedUrl ?: "https://levgames.nl/jonazwetsloot/chat/api/")

        // Adjust for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Override the back button behavior to support back swipe functionality
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack() // Go to the last page in the WebView's history
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Save the current URL when the activity is paused
    override fun onPause() {
        super.onPause()
        val currentUrl = webView.url
        sharedPreferences.edit().putString("lastVisitedUrl", currentUrl).apply() // Save the current URL to SharedPreferences
    }

    // Custom WebViewClient to handle external links
    private class MyWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null || view == null) {
                return false
            }
            if (view.url?.startsWith("https://jonazwetsloot.nl/chat/") == false && view.url?.startsWith("https://levgames.nl/jonazwetsloot/chat/") == false) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ContextCompat.startActivity(view.context, browserIntent, null)
                return true
            }
            return false
        }
    }
}
