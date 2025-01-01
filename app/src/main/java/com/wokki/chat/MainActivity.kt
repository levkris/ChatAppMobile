package com.wokki.chat

import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var rootLayout: ConstraintLayout
    private var initialLayoutTop: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("WebViewPrefs", MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        rootLayout = findViewById(R.id.main)  // Use ConstraintLayout here

        // Enable JavaScript
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true

        // Enable LocalStorage
        webSettings.domStorageEnabled = true

        // Disable zooming
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false

        // Enable wide viewport and set zoomed out to the max
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        // Set up the WebViewClient to handle links inside the WebView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Inject JavaScript to hide the androidAppNotif element after the page is loaded
                val jsCode = """
                    var androidAppNotif = document.getElementById('androidAppNotif');
                    if (androidAppNotif) {
                        androidAppNotif.style.display = 'none';
                    }
                """
                // Execute JavaScript to hide the element
                webView.evaluateJavascript(jsCode, null)
            }
        }

        // Load the last visited URL from SharedPreferences or a default URL
        val lastVisitedUrl = sharedPreferences.getString("lastVisitedUrl", "https://levgames.nl/jonazwetsloot/chat/api/")
        webView.loadUrl(lastVisitedUrl ?: "https://levgames.nl/jonazwetsloot/chat/api/")

        // Adjust for system bars
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Ensure textboxes move above the keyboard
        adjustWebViewForKeyboard()
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

    // Adjust WebView content and move the entire layout upwards when the keyboard is visible
    private fun adjustWebViewForKeyboard() {
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootLayout.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootLayout.rootView.height
            val keypadHeight = screenHeight - r.bottom

            // If the keyboard is visible, move the entire layout upwards
            if (keypadHeight > screenHeight * 0.15) {
                // Save the initial top position of the layout
                if (initialLayoutTop == 0) {
                    initialLayoutTop = rootLayout.top
                }

                val translationY = -keypadHeight.toFloat()  // Move up by the height of the keyboard
                rootLayout.translationY = translationY  // Move the layout upwards
            } else {
                // Restore the layout's original position when the keyboard is hidden
                rootLayout.translationY = 0f
            }
        }
    }
}
